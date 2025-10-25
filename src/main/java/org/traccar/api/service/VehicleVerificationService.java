/*
 * Copyright 2025 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.VehicleVerification;
import org.traccar.model.TransporterVehicleMap;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

@Singleton
public class VehicleVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VehicleVerificationService.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Inject
    private Config config;

    @Inject
    private Storage storage;

    @Inject
    private ValidationLogService validationLogService;

    /**
     * Check if any validity date has expired or is missing
     * Note: Returns true if critical fields (insurance, PUCC, fitness) are null or
     * expired
     */
    private boolean isExpired(VehicleVerification vehicle) {
        Date now = new Date();

        // Check RC status - if not ACTIVE, consider expired
        if (vehicle.getRcStatus() != null && !vehicle.getRcStatus().equalsIgnoreCase("ACTIVE")) {
            return true;
        }

        // Check insurance validity (critical - must exist and be valid)
        if (vehicle.getInsuranceExpiry() == null) {
            return true; // Insurance date missing, need to refetch
        }
        if (vehicle.getInsuranceExpiry().before(now)) {
            return true; // Insurance expired
        }

        // Check PUCC validity (critical - must exist and be valid)
        if (vehicle.getPuccUpto() == null) {
            return true; // PUCC date missing, need to refetch
        }
        if (vehicle.getPuccUpto().before(now)) {
            return true; // PUCC expired
        }

        // Check fitness validity (critical - must exist and be valid)
        if (vehicle.getFitUpTo() == null) {
            return true; // Fitness date missing, need to refetch
        }
        if (vehicle.getFitUpTo().before(now)) {
            return true; // Fitness expired
        }

        // Check permit validity (only if permit exists)
        if (vehicle.getPermitValidUpto() != null && vehicle.getPermitValidUpto().before(now)) {
            return true;
        }

        // Check national permit validity (only if national permit exists)
        if (vehicle.getNationalPermitUpto() != null && vehicle.getNationalPermitUpto().before(now)) {
            return true;
        }

        // Check tax validity (only if tax date exists)
        if (vehicle.getTaxUpto() != null && vehicle.getTaxUpto().before(now)) {
            return true;
        }

        return false;
    }

    /**
     * Fetch vehicle data from DB and check if refresh is needed
     */
    public VehicleVerification getVehicleData(long userId, String vehicleNumber, String transporterName)
            throws Exception {
        // Normalize inputs
        vehicleNumber = vehicleNumber.toUpperCase();
        transporterName = transporterName != null ? transporterName.toUpperCase() : null;

        // Search in database
        VehicleVerification vehicle = storage.getObject(VehicleVerification.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("userId", userId),
                        new Condition.Equals("licensePlate", vehicleNumber))));

        boolean expired = (vehicle == null || isExpired(vehicle));

        // Check transporter mapping
        TransporterVehicleMap mapping = storage.getObject(TransporterVehicleMap.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("userId", userId),
                        new Condition.Equals("vehicleNumber", vehicleNumber))));

        if (mapping != null && !mapping.getTransporterName().equals(transporterName)) {
            throw new Exception("Vehicle already exists with another transporter");
        }

        // Enforce transporterName selection from master list
        if (transporterName == null || transporterName.isBlank()) {
            throw new Exception("Transporter name is required and must be selected from the list");
        }
        // Validate transporter exists for user
        org.traccar.model.Transporter transporter = storage.getObject(org.traccar.model.Transporter.class,
                new Request(new Columns.All(), new Condition.And(
                        new Condition.Equals("userId", userId),
                        new Condition.Equals("name", transporterName.toUpperCase()))));
        if (transporter == null) {
            throw new Exception("Invalid transporter name");
        }

        if (expired) {
            LOGGER.info("Vehicle {} not found or expired, fetching from external API", vehicleNumber);
            vehicle = fetchFromExternalApi(vehicleNumber);
            if (vehicle != null) {
                // enforce per-user ownership on newly fetched entity
                vehicle.setUserId(userId);
                saveOrUpdateVehicle(vehicle);

                // Log successful validation from external API with actual validity status
                String logStatus = "valid".equalsIgnoreCase(vehicle.getOverallValidityStatus()) ? "pass" : "fail";
                validationLogService.logValidation(userId, "vehicle", vehicleNumber, logStatus);
            }

            // Insert mapping if not exists
            if (mapping == null && transporterName != null) {
                TransporterVehicleMap newMap = new TransporterVehicleMap();
                newMap.setUserId(userId);
                newMap.setTransporterName(transporterName);
                newMap.setVehicleNumber(vehicleNumber);
                Date now = new Date();
                newMap.setCreatedAt(now);
                newMap.setUpdatedAt(now);
                storage.addObject(newMap, new Request(new Columns.Exclude("id")));
            }
        } else {
            // Log successful validation from cache with actual validity status
            if (vehicle != null) {
                String logStatus = "valid".equalsIgnoreCase(vehicle.getOverallValidityStatus()) ? "pass" : "fail";
                validationLogService.logValidation(userId, "vehicle", vehicleNumber, logStatus);
            }

            // If mapping does not exist, insert
            if (mapping == null && transporterName != null) {
                TransporterVehicleMap newMap = new TransporterVehicleMap();
                newMap.setUserId(userId);
                newMap.setTransporterName(transporterName);
                newMap.setVehicleNumber(vehicleNumber);
                Date now = new Date();
                newMap.setCreatedAt(now);
                newMap.setUpdatedAt(now);
                storage.addObject(newMap, new Request(new Columns.Exclude("id")));
            }
        }

        return vehicle;
    }

    /**
     * Call external RC API to fetch vehicle data
     */
    private VehicleVerification fetchFromExternalApi(String vehicleNumber) throws Exception {
        String apiUrl = config.getString(Keys.VEHICLE_RC_API_URL);
        String requestId = config.getString(Keys.VEHICLE_RC_API_REQUEST_ID);
        String apiKey = config.getString(Keys.VEHICLE_RC_API_KEY);

        // Build request body
        JSONObject requestBody = new JSONObject();
        requestBody.put("vehicleId", vehicleNumber.toUpperCase());

        // Build HTTP request
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .header("Referer", "docs.apiclub.in")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8));

        // Add optional request ID header
        if (requestId != null && !requestId.isEmpty()) {
            requestBuilder.header("x-request-id", requestId);
        }

        // Add API key header
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("x-api-key", apiKey);
        }

        HttpRequest request = requestBuilder.build();

        // Send request
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return parseApiResponse(response.body(), vehicleNumber);
        } else {
            LOGGER.error("External API error: status={}, body={}", response.statusCode(), response.body());
            throw new Exception("External API returned error: " + response.statusCode());
        }
    }

    /**
     * Parse API response and create VehicleValidity object
     */
    private VehicleVerification parseApiResponse(String responseBody, String vehicleNumber) {
        try {
            JSONObject rootJson = new JSONObject(responseBody);

            // Extract the "response" object which contains the actual vehicle data
            if (!rootJson.has("response")) {
                LOGGER.error("API response missing 'response' field");
                return null;
            }

            JSONObject json = rootJson.getJSONObject("response");
            VehicleVerification vehicle = new VehicleVerification();
            vehicle.setLicensePlate(vehicleNumber.toUpperCase());

            // Parse all fields from API response
            if (json.has("chassis_number")) {
                vehicle.setChassisNumber(json.optString("chassis_number", null));
            }
            if (json.has("engine_number")) {
                vehicle.setEngineNumber(json.optString("engine_number", null));
            }
            if (json.has("owner_name")) {
                vehicle.setOwnerName(json.optString("owner_name", null));
            }
            if (json.has("father_name")) {
                vehicle.setFatherName(json.optString("father_name", null));
            }
            if (json.has("present_address")) {
                vehicle.setPresentAddress(json.optString("present_address", null));
            }
            if (json.has("permanent_address")) {
                vehicle.setPermanentAddress(json.optString("permanent_address", null));
            }
            if (json.has("is_financed")) {
                vehicle.setIsFinanced(json.optString("is_financed", null));
            }
            if (json.has("financer")) {
                vehicle.setFinancer(json.optString("financer", null));
            }
            if (json.has("insurance_company")) {
                vehicle.setInsuranceCompany(json.optString("insurance_company", null));
            }
            if (json.has("insurance_policy")) {
                vehicle.setInsurancePolicy(json.optString("insurance_policy", null));
            }
            if (json.has("insurance_expiry")) {
                vehicle.setInsuranceExpiry(parseDate(json.optString("insurance_expiry", null)));
            }
            if (json.has("class")) {
                vehicle.setVehicleClass(json.optString("class", null));
            }
            if (json.has("registration_date")) {
                vehicle.setRegistrationDate(parseDate(json.optString("registration_date", null)));
            }
            if (json.has("vehicle_age")) {
                vehicle.setVehicleAge(json.optString("vehicle_age", null));
            }
            if (json.has("pucc_upto")) {
                vehicle.setPuccUpto(parseDate(json.optString("pucc_upto", null)));
            }
            if (json.has("pucc_number")) {
                vehicle.setPuccNumber(json.optString("pucc_number", null));
            }
            if (json.has("fuel_type")) {
                vehicle.setFuelType(json.optString("fuel_type", null));
            }
            if (json.has("brand_name")) {
                vehicle.setBrandName(json.optString("brand_name", null));
            }
            if (json.has("brand_model")) {
                vehicle.setBrandModel(json.optString("brand_model", null));
            }
            if (json.has("cubic_capacity")) {
                vehicle.setCubicCapacity(json.optString("cubic_capacity", null));
            }
            if (json.has("gross_weight")) {
                vehicle.setGrossWeight(json.optString("gross_weight", null));
            }
            if (json.has("cylinders")) {
                vehicle.setCylinders(json.optString("cylinders", null));
            }
            if (json.has("color")) {
                vehicle.setColor(json.optString("color", null));
            }
            if (json.has("norms")) {
                vehicle.setNorms(json.optString("norms", null));
            }
            if (json.has("noc_details")) {
                vehicle.setNocDetails(json.optString("noc_details", null));
            }
            if (json.has("seating_capacity")) {
                vehicle.setSeatingCapacity(json.optString("seating_capacity", null));
            }
            if (json.has("owner_count")) {
                vehicle.setOwnerCount(json.optString("owner_count", null));
            }
            if (json.has("tax_upto")) {
                vehicle.setTaxUpto(parseDate(json.optString("tax_upto", null)));
            }
            if (json.has("tax_paid_upto")) {
                vehicle.setTaxPaidUpto(json.optString("tax_paid_upto", null));
            }
            if (json.has("permit_number")) {
                vehicle.setPermitNumber(json.optString("permit_number", null));
            }
            if (json.has("permit_issue_date")) {
                vehicle.setPermitIssueDate(parseDate(json.optString("permit_issue_date", null)));
            }
            if (json.has("permit_valid_from")) {
                vehicle.setPermitValidFrom(parseDate(json.optString("permit_valid_from", null)));
            }
            if (json.has("permit_valid_upto")) {
                vehicle.setPermitValidUpto(parseDate(json.optString("permit_valid_upto", null)));
            }
            if (json.has("permit_type")) {
                vehicle.setPermitType(json.optString("permit_type", null));
            }
            if (json.has("national_permit_number")) {
                vehicle.setNationalPermitNumber(json.optString("national_permit_number", null));
            }
            if (json.has("national_permit_upto")) {
                vehicle.setNationalPermitUpto(parseDate(json.optString("national_permit_upto", null)));
            }
            if (json.has("national_permit_issued_by")) {
                vehicle.setNationalPermitIssuedBy(json.optString("national_permit_issued_by", null));
            }
            if (json.has("rc_status")) {
                vehicle.setRcStatus(json.optString("rc_status", null));
            }
            if (json.has("category")) {
                vehicle.setCategory(json.optString("category", null));
            }
            if (json.has("body_type")) {
                vehicle.setBodyType(json.optString("body_type", null));
            }
            if (json.has("fit_up_to")) {
                vehicle.setFitUpTo(parseDate(json.optString("fit_up_to", null)));
            }
            if (json.has("manufacturing_date")) {
                vehicle.setManufacturingDate(json.optString("manufacturing_date", null));
            }
            if (json.has("manufacturing_date_formatted")) {
                vehicle.setManufacturingDateFormatted(json.optString("manufacturing_date_formatted", null));
            }
            if (json.has("rto_name")) {
                vehicle.setRtoName(json.optString("rto_name", null));
            }
            if (json.has("latest_by")) {
                vehicle.setLatestBy(json.optString("latest_by", null));
            }
            if (json.has("sleeper_capacity")) {
                vehicle.setSleeperCapacity(json.optString("sleeper_capacity", null));
            }
            if (json.has("standing_capacity")) {
                vehicle.setStandingCapacity(json.optString("standing_capacity", null));
            }
            if (json.has("wheelbase")) {
                vehicle.setWheelbase(json.optString("wheelbase", null));
            }
            if (json.has("unladen_weight")) {
                vehicle.setUnladenWeight(json.optString("unladen_weight", null));
            }

            // Compute overall validity status based on critical fields
            Date now = new Date();
            boolean fitValid = vehicle.getFitUpTo() != null && vehicle.getFitUpTo().after(now);
            boolean puccValid = vehicle.getPuccUpto() != null && vehicle.getPuccUpto().after(now);
            boolean insuranceValid = vehicle.getInsuranceExpiry() != null && vehicle.getInsuranceExpiry().after(now);

            if (fitValid && puccValid && insuranceValid) {
                vehicle.setOverallValidityStatus("valid");
            } else {
                vehicle.setOverallValidityStatus("expired");
            }

            vehicle.setUpdatedAt(new Date());

            return vehicle;
        } catch (Exception e) {
            LOGGER.error("Failed to parse API response", e);
            return null;
        }
    }

    /**
     * Parse date string from API (format: yyyy-MM-dd)
     */
    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return DATE_FORMAT.parse(dateStr);
        } catch (ParseException e) {
            LOGGER.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    /**
     * Save or update vehicle in database
     */
    private void saveOrUpdateVehicle(VehicleVerification vehicle) throws StorageException {
        // Check if vehicle already exists
        VehicleVerification existing = storage.getObject(VehicleVerification.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("userId", vehicle.getUserId()),
                        new Condition.Equals("licensePlate", vehicle.getLicensePlate()))));

        if (existing != null) {
            // Update existing record
            vehicle.setId(existing.getId());
            vehicle.setCreatedAt(existing.getCreatedAt());
            storage.updateObject(vehicle, new Request(
                    new Columns.Exclude("id", "createdAt"),
                    new Condition.Equals("id", vehicle.getId())));
            LOGGER.info("Updated vehicle data for {}", vehicle.getLicensePlate());
        } else {
            // Insert new record
            vehicle.setCreatedAt(new Date());
            vehicle.setId(storage.addObject(vehicle, new Request(new Columns.Exclude("id"))));
            LOGGER.info("Inserted new vehicle data for {}", vehicle.getLicensePlate());
        }
    }

    /**
     * Generate response with validity status
     */
    public JSONObject buildResponse(VehicleVerification vehicle) {
        JSONObject response = new JSONObject();
        Date now = new Date();

        // Basic Vehicle Information
        response.put("vehicleNumber", vehicle.getLicensePlate());
        response.put("chassisNo", vehicle.getChassisNumber());
        response.put("engineNo", vehicle.getEngineNumber());
        response.put("makerName", vehicle.getBrandName());
        response.put("modelName", vehicle.getBrandModel());

        // Registration Information
        if (vehicle.getRegistrationDate() != null) {
            response.put("registrationDate", DATE_FORMAT.format(vehicle.getRegistrationDate()));
        } else {
            response.put("registrationDate", JSONObject.NULL);
        }

        // Permit Information
        response.put("permitType", vehicle.getPermitType());
        response.put("permitNo", vehicle.getPermitNumber());
        if (vehicle.getPermitIssueDate() != null) {
            response.put("permitIssueDate", DATE_FORMAT.format(vehicle.getPermitIssueDate()));
        } else {
            response.put("permitIssueDate", JSONObject.NULL);
        }
        if (vehicle.getPermitValidFrom() != null) {
            response.put("permitValidFrom", DATE_FORMAT.format(vehicle.getPermitValidFrom()));
        } else {
            response.put("permitValidFrom", JSONObject.NULL);
        }
        if (vehicle.getPermitValidUpto() != null) {
            response.put("permitValidUpto", DATE_FORMAT.format(vehicle.getPermitValidUpto()));
            response.put("permitValidityStatus",
                    vehicle.getPermitValidUpto().after(now) ? "valid" : "expired");
        } else {
            response.put("permitValidUpto", JSONObject.NULL);
            response.put("permitValidityStatus", "unknown");
        }

        // Tax Information
        if (vehicle.getTaxUpto() != null) {
            response.put("taxValidUpto", DATE_FORMAT.format(vehicle.getTaxUpto()));
            response.put("taxValidityStatus",
                    vehicle.getTaxUpto().after(now) ? "valid" : "expired");
        } else {
            response.put("taxValidUpto", JSONObject.NULL);
            response.put("taxValidityStatus", "unknown");
        }

        // Vehicle Class and Description
        response.put("vehicleClass", vehicle.getVehicleClass());
        response.put("fuelType", vehicle.getFuelType());
        response.put("emissionNorm", vehicle.getNorms());

        // Insurance Information
        response.put("insurancePolicyNo", vehicle.getInsurancePolicy());
        if (vehicle.getInsuranceExpiry() != null) {
            response.put("insuranceValidUpto", DATE_FORMAT.format(vehicle.getInsuranceExpiry()));
            response.put("insuranceValidityStatus",
                    vehicle.getInsuranceExpiry().after(now) ? "valid" : "expired");
        } else {
            response.put("insuranceValidUpto", JSONObject.NULL);
            response.put("insuranceValidityStatus", "unknown");
        }

        // Fitness validity
        if (vehicle.getFitUpTo() != null) {
            response.put("fitnessValidUpto", DATE_FORMAT.format(vehicle.getFitUpTo()));
            response.put("fitnessValidityStatus",
                    vehicle.getFitUpTo().after(now) ? "valid" : "expired");
        } else {
            response.put("fitnessValidUpto", JSONObject.NULL);
            response.put("fitnessValidityStatus", "unknown");
        }

        // PUCC Information
        response.put("puccNo", vehicle.getPuccNumber());
        if (vehicle.getPuccUpto() != null) {
            response.put("puccValidUpto", DATE_FORMAT.format(vehicle.getPuccUpto()));
            response.put("puccValidityStatus",
                    vehicle.getPuccUpto().after(now) ? "valid" : "expired");
        } else {
            response.put("puccValidUpto", JSONObject.NULL);
            response.put("puccValidityStatus", "unknown");
        }

        // Additional Information
        response.put("registeringAuthority", vehicle.getRtoName());
        response.put("ownerName", vehicle.getOwnerName());
        response.put("rcStatus", vehicle.getRcStatus());

        // Overall Validity Status (based on critical fields)
        response.put("overallValidityStatus", vehicle.getOverallValidityStatus());

        return response;
    }
}
