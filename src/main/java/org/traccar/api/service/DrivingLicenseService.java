package org.traccar.api.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.DrivingLicenseRecord;
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
import java.util.Map;

@Singleton
public class DrivingLicenseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DrivingLicenseService.class);
    private static final SimpleDateFormat DL_DATE = new SimpleDateFormat("dd-MM-yyyy");

    @Inject
    private Config config;

    @Inject
    private Storage storage;

    @Inject
    private ValidationLogService validationLogService;

    private boolean isValid(DrivingLicenseRecord dl) {
        if (dl == null)
            return false;
        Date now = new Date();
        Date overall = dl.getOverallExpiryDate();
        if (overall != null) {
            return overall.after(now);
        }
        // fallback: if either non/transport present and after now
        if (dl.getNonTransportValidTo() != null && dl.getNonTransportValidTo().after(now))
            return true;
        if (dl.getTransportValidTo() != null && dl.getTransportValidTo().after(now))
            return true;
        return false;
    }

    public DrivingLicenseRecord getOrFetch(long userId, String dlNo, String dob) throws Exception {
        dlNo = dlNo.trim().toUpperCase();
        dob = dob.trim();

        DrivingLicenseRecord existing = storage.getObject(DrivingLicenseRecord.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("userId", userId),
                        new Condition.Equals("dlNo", dlNo))));

        if (existing != null && isValid(existing)) {
            // Log successful validation from cache with actual validity status
            String logStatus = "valid".equalsIgnoreCase(existing.getOverallExpiryStatus()) ? "pass" : "fail";
            validationLogService.logValidation(userId, "license", dlNo, logStatus);
            return existing;
        }

        // fetch from external
        DrivingLicenseRecord fetched = fetchFromExternal(dlNo, dob);
        if (fetched == null) {
            return existing; // may be null
        }
        fetched.setUserId(userId);
        fetched.setDob(dob);
        upsert(fetched);

        // Log successful validation from external API with actual validity status
        String logStatus = "valid".equalsIgnoreCase(fetched.getOverallExpiryStatus()) ? "pass" : "fail";
        validationLogService.logValidation(userId, "license", dlNo, logStatus);

        return fetched;
    }

    private DrivingLicenseRecord fetchFromExternal(String dlNo, String dob) throws Exception {
        String apiUrl = config.getString(Keys.DL_API_URL);
        String apiKey = config.getString(Keys.DL_API_KEY);
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("DL API URL not configured");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DL API key not configured");
        }

        JSONObject body = new JSONObject(Map.of(
                "dl_no", dlNo,
                "dob", dob));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(30))
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .header("Referer", "docs.apiclub.in")
                .header("x-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.error("DL API error: status={}, body={}", response.statusCode(), response.body());
            throw new RuntimeException("External DL API error: " + response.statusCode());
        }
        return parseApiResponse(response.body(), dlNo, dob);
    }

    private DrivingLicenseRecord parseApiResponse(String responseBody, String requestDlNo, String requestDob) {
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONObject resp = root.optJSONObject("response");
            if (resp == null) {
                LOGGER.warn("DL API response missing 'response' object");
                return null;
            }

            DrivingLicenseRecord dl = new DrivingLicenseRecord();
            // override identifiers from response when present
            String respDlNo = resp.optString("license_number", null);
            if (respDlNo != null && !respDlNo.isBlank()) {
                dl.setDlNo(respDlNo.trim().toUpperCase());
            } else {
                dl.setDlNo(requestDlNo.toUpperCase());
            }
            String respDob = resp.optString("dob", null);
            if (respDob != null && !respDob.isBlank()) {
                dl.setDob(respDob.trim());
            } else if (requestDob != null) {
                dl.setDob(requestDob.trim());
            }
            dl.setHolderName(resp.optString("holder_name", null));
            dl.setGender(resp.optString("gender", null));
            dl.setStatus(resp.optString("status", null));
            dl.setFatherOrHusbandName(resp.optString("father_or_husband_name", null));
            dl.setPresentAddress(resp.optString("present_address", null));
            dl.setPermanentAddress(resp.optString("permanent_address", null));
            dl.setIssueDate(resp.optString("issue_date", null));
            dl.setRtoCode(resp.optString("rto_code", null));
            dl.setRto(resp.optString("rto", null));
            dl.setState(resp.optString("state", null));

            // validity objects
            JSONObject nt = resp.optJSONObject("non_transport_validity");
            if (nt != null) {
                dl.setNonTransportValidFrom(parseDate(nt.optString("from", null)));
                dl.setNonTransportValidTo(parseDate(nt.optString("to", null)));
            }
            JSONObject tr = resp.optJSONObject("transport_validity");
            if (tr != null) {
                dl.setTransportValidFrom(parseDate(tr.optString("from", null)));
                dl.setTransportValidTo(parseDate(tr.optString("to", null)));
            }
            // simple validity fallback
            dl.setValidFrom(parseDate(resp.optString("valid_from", null)));
            dl.setValidUpto(parseDate(resp.optString("valid_upto", null)));

            // vehicle_class can be array or string; store safely
            try {
                JSONArray vc = resp.optJSONArray("vehicle_class");
                if (vc != null) {
                    dl.setVehicleClassJson(vc.toString());
                } else {
                    String vcStr = resp.optString("vehicle_class", null);
                    if (vcStr != null && !vcStr.isBlank()) {
                        dl.setVehicleClassJson(new org.json.JSONArray().put(vcStr).toString());
                    }
                }
            } catch (Exception ignore) {
                // keep null on failure
            }
            dl.setImage(resp.optString("image", null));
            dl.setBloodGroup(resp.optString("blood_group", null));

            // compute statuses
            computeStatuses(dl);

            // Store only inner 'response' JSON without request_id
            JSONObject sanitized = new JSONObject(resp.toString());
            sanitized.remove("request_id");
            dl.setRawResponse(sanitized.toString()); // TODO: revisit storage size impact
            dl.setUpdatedAt(new Date());
            return dl;
        } catch (Exception e) {
            LOGGER.error("Failed to parse DL API response", e);
            return null;
        }
    }

    private void computeStatuses(DrivingLicenseRecord dl) {
        Date now = new Date();
        String ntStatus = "unknown";
        if (dl.getNonTransportValidTo() != null) {
            ntStatus = dl.getNonTransportValidTo().after(now) ? "valid" : "expired";
        }
        String trStatus = "unknown";
        if (dl.getTransportValidTo() != null) {
            trStatus = dl.getTransportValidTo().after(now) ? "valid" : "expired";
        }
        dl.setNonTransportExpiryStatus(ntStatus);
        dl.setTransportExpiryStatus(trStatus);

        Date overall = null;
        if (dl.getNonTransportValidTo() != null)
            overall = dl.getNonTransportValidTo();
        if (dl.getTransportValidTo() != null) {
            if (overall == null || dl.getTransportValidTo().after(overall)) {
                overall = dl.getTransportValidTo();
            }
        }
        // fallback to simple valid_upto when split validity is absent
        if (overall == null && dl.getValidUpto() != null) {
            overall = dl.getValidUpto();
        }
        dl.setOverallExpiryDate(overall);
        String overallStatus = "unknown";
        if (overall != null) {
            overallStatus = overall.after(now) ? "valid" : "expired";
        }
        dl.setOverallExpiryStatus(overallStatus);
    }

    private Date parseDate(String ddMMyyyy) {
        if (ddMMyyyy == null || ddMMyyyy.isBlank())
            return null;
        try {
            return DL_DATE.parse(ddMMyyyy);
        } catch (ParseException e) {
            LOGGER.warn("Failed to parse DL date: {}", ddMMyyyy);
            return null;
        }
    }

    private void upsert(DrivingLicenseRecord dl) throws StorageException {
        DrivingLicenseRecord existing = storage.getObject(DrivingLicenseRecord.class, new Request(
                new Columns.All(),
                new Condition.And(
                        new Condition.Equals("userId", dl.getUserId()),
                        new Condition.Equals("dlNo", dl.getDlNo()))));

        if (existing != null) {
            dl.setId(existing.getId());
            dl.setCreatedAt(existing.getCreatedAt());
            storage.updateObject(dl, new Request(new Columns.Exclude("id", "createdAt"),
                    new Condition.Equals("id", dl.getId())));
        } else {
            dl.setCreatedAt(new Date());
            dl.setId(storage.addObject(dl, new Request(new Columns.Exclude("id"))));
        }
    }

    public JSONObject buildResponse(DrivingLicenseRecord dl) {
        JSONObject out = new JSONObject();
        out.put("code", 200);
        out.put("status", "success");
        out.put("message", "Success");
        out.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date()));

        // Try to reuse original response->response object if present
        JSONObject original = null;
        try {
            if (dl.getRawResponse() != null) {
                original = new JSONObject(dl.getRawResponse());
            }
        } catch (Exception ignored) {
        }
        if (original == null) {
            original = new JSONObject();
            original.put("license_number", dl.getDlNo());
            original.put("holder_name", dl.getHolderName());
            original.put("dob", dl.getDob());
        }

        // ensure request_id is not present
        original.remove("request_id");

        // append computed statuses
        original.put("non_transport_expiry_status", dl.getNonTransportExpiryStatus());
        original.put("transport_expiry_status", dl.getTransportExpiryStatus());
        original.put("overall_expiry_status", dl.getOverallExpiryStatus());
        if (dl.getOverallExpiryDate() != null) {
            original.put("overall_expiry_date", DL_DATE.format(dl.getOverallExpiryDate()));
        } else {
            original.put("overall_expiry_date", JSONObject.NULL);
        }

        out.put("response", original);
        return out;
    }
}
