/*
 * Copyright 2024 Anton Tananaev (anton@traccar.org)
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
package org.traccar.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.traccar.storage.QueryIgnore;
import org.traccar.storage.StorageName;

import java.util.Date;

@Schema(description = "Trip/Shipment record with device tracking and delivery information")
@StorageName("tc_trips")
public class Trip extends ExtendedModel {

    private String name;

    @Schema(description = "Trip name/identifier", example = "Mumbai Delivery #123")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Asset/Device Information
    private long deviceId;

    @Schema(description = "[REQUIRED] Device/vehicle ID for tracking. Alternative: use deviceUniqueId",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1234")
    public long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(long deviceId) {
        this.deviceId = deviceId;
    }

    private String deviceUniqueId;

    public String getDeviceUniqueId() {
        return deviceUniqueId;
    }

    public void setDeviceUniqueId(String deviceUniqueId) {
        this.deviceUniqueId = deviceUniqueId;
    }

    private String deviceName;

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    private String assetNumber;

    public String getAssetNumber() {
        return assetNumber;
    }

    public void setAssetNumber(String assetNumber) {
        this.assetNumber = assetNumber;
    }

    private String assetType;

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    // Transporter Information
    private String transporterName;

    public String getTransporterName() {
        return transporterName;
    }

    public void setTransporterName(String transporterName) {
        this.transporterName = transporterName;
    }

    private String otherTransporterName;

    public String getOtherTransporterName() {
        return otherTransporterName;
    }

    public void setOtherTransporterName(String otherTransporterName) {
        this.otherTransporterName = otherTransporterName;
    }

    // Consignor/Consignee Information
    private long consignorId;

    @Schema(description = "[REQUIRED] Consignor/Shipper user ID. If user not found, provide otherConsignorName",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    public long getConsignorId() {
        return consignorId;
    }

    public void setConsignorId(long consignorId) {
        this.consignorId = consignorId;
    }

    private String consignorName;

    @Schema(description = "Consignor name (auto-filled from user record)",
            accessMode = Schema.AccessMode.READ_ONLY)
    public String getConsignorName() {
        return consignorName;
    }

    public void setConsignorName(String consignorName) {
        this.consignorName = consignorName;
    }

    private String otherConsignorName;

    @Schema(description = "Custom consignor name if consignorId doesn't match a user",
            example = "External Shipper Inc")
    public String getOtherConsignorName() {
        return otherConsignorName;
    }

    public void setOtherConsignorName(String otherConsignorName) {
        this.otherConsignorName = otherConsignorName;
    }

    private long consigneeId;

    @Schema(description = "[REQUIRED] Consignee/Receiver user ID. If user not found, provide otherConsigneeName",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    public long getConsigneeId() {
        return consigneeId;
    }

    public void setConsigneeId(long consigneeId) {
        this.consigneeId = consigneeId;
    }

    private String consigneeName;

    public String getConsigneeName() {
        return consigneeName;
    }

    public void setConsigneeName(String consigneeName) {
        this.consigneeName = consigneeName;
    }

    private String otherConsigneeName;

    public String getOtherConsigneeName() {
        return otherConsigneeName;
    }

    public void setOtherConsigneeName(String otherConsigneeName) {
        this.otherConsigneeName = otherConsigneeName;
    }

    private String consigneePincode;

    public String getConsigneePincode() {
        return consigneePincode;
    }

    public void setConsigneePincode(String consigneePincode) {
        this.consigneePincode = consigneePincode;
    }

    // Origin and Destination
    private double originLatitude;

    @Schema(description = "[REQUIRED] Starting point latitude (-90 to 90, cannot be exactly 0,0)",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "28.6139", minimum = "-90", maximum = "90")
    public double getOriginLatitude() {
        return originLatitude;
    }

    public void setOriginLatitude(double originLatitude) {
        this.originLatitude = originLatitude;
    }

    private double originLongitude;

    @Schema(description = "[REQUIRED] Starting point longitude (-180 to 180, cannot be exactly 0,0)",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "77.2090", minimum = "-180", maximum = "180")
    public double getOriginLongitude() {
        return originLongitude;
    }

    public void setOriginLongitude(double originLongitude) {
        this.originLongitude = originLongitude;
    }

    private String originAddress;

    @Schema(description = "Origin address text", example = "Connaught Place, New Delhi")
    public String getOriginAddress() {
        return originAddress;
    }

    public void setOriginAddress(String originAddress) {
        this.originAddress = originAddress;
    }

    private double destinationLatitude;

    @Schema(description = "[REQUIRED] Destination latitude (-90 to 90, cannot be exactly 0,0)",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "19.0760", minimum = "-90", maximum = "90")
    public double getDestinationLatitude() {
        return destinationLatitude;
    }

    public void setDestinationLatitude(double destinationLatitude) {
        this.destinationLatitude = destinationLatitude;
    }

    private double destinationLongitude;

    @Schema(description = "[REQUIRED] Destination longitude (-180 to 180, cannot be exactly 0,0)",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "72.8777", minimum = "-180", maximum = "180")
    public double getDestinationLongitude() {
        return destinationLongitude;
    }

    public void setDestinationLongitude(double destinationLongitude) {
        this.destinationLongitude = destinationLongitude;
    }

    private String destinationAddress;

    public String getDestinationAddress() {
        return destinationAddress;
    }

    public void setDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
    }

    // Date and Time Information
    private Date departureDate;

    @Schema(description = "Trip departure/start date. Defaults to current time if not provided",
            example = "2025-12-02T10:00:00Z")
    public Date getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(Date departureDate) {
        this.departureDate = departureDate;
    }

    private Date etaDate;

    @Schema(description = "[REQUIRED] Expected Time of Arrival. Must be in the future for new trips",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "2025-12-03T18:00:00Z")
    public Date getEtaDate() {
        return etaDate;
    }

    public void setEtaDate(Date etaDate) {
        this.etaDate = etaDate;
    }

    private Date bookingDate;

    @Schema(description = "Booking/order date", example = "2025-12-01T08:00:00Z")
    public Date getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(Date bookingDate) {
        this.bookingDate = bookingDate;
    }

    // Driver Information
    private String driverNumber;

    public String getDriverNumber() {
        return driverNumber;
    }

    public void setDriverNumber(String driverNumber) {
        this.driverNumber = driverNumber;
    }

    private String driverName;

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    private String driverMobile;

    public String getDriverMobile() {
        return driverMobile;
    }

    public void setDriverMobile(String driverMobile) {
        this.driverMobile = driverMobile;
    }

    private String driverContact;

    public String getDriverContact() {
        return driverContact;
    }

    public void setDriverContact(String driverContact) {
        this.driverContact = driverContact;
    }

    private String driverLicense;

    public String getDriverLicense() {
        return driverLicense;
    }

    public void setDriverLicense(String driverLicense) {
        this.driverLicense = driverLicense;
    }

    private long driverId;

    public long getDriverId() {
        return driverId;
    }

    public void setDriverId(long driverId) {
        this.driverId = driverId;
    }

    // Shipper Information
    private String shipperName;

    public String getShipperName() {
        return shipperName;
    }

    public void setShipperName(String shipperName) {
        this.shipperName = shipperName;
    }

    private String shipmentId;

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    // Waybill Information
    private String waybillNumber;

    public String getWaybillNumber() {
        return waybillNumber;
    }

    public void setWaybillNumber(String waybillNumber) {
        this.waybillNumber = waybillNumber;
    }

    private Date waybillExpiryDate;

    public Date getWaybillExpiryDate() {
        return waybillExpiryDate;
    }

    public void setWaybillExpiryDate(Date waybillExpiryDate) {
        this.waybillExpiryDate = waybillExpiryDate;
    }

    private String waybillImage;

    public String getWaybillImage() {
        return waybillImage;
    }

    public void setWaybillImage(String waybillImage) {
        this.waybillImage = waybillImage;
    }

    // Material Information
    private String material;

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    private String otherMaterial;

    public String getOtherMaterial() {
        return otherMaterial;
    }

    public void setOtherMaterial(String otherMaterial) {
        this.otherMaterial = otherMaterial;
    }

    // Invoice Information
    private String invoiceNumber;

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    private String invoiceQuantity;

    public String getInvoiceQuantity() {
        return invoiceQuantity;
    }

    public void setInvoiceQuantity(String invoiceQuantity) {
        this.invoiceQuantity = invoiceQuantity;
    }

    // LR/GR Information
    private String lrGrNumber;

    public String getLrGrNumber() {
        return lrGrNumber;
    }

    public void setLrGrNumber(String lrGrNumber) {
        this.lrGrNumber = lrGrNumber;
    }

    // Card Number
    private String cardNumber;

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    // Vehicle Information
    private String vehicleType;

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    private String vehicleNumber;

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    // Current Location Tracking (transient - not stored in DB, populated
    // dynamically from device position)
    private transient double currentLatitude;

    @Schema(description = "Current device latitude (read-only, enriched from device position)",
            accessMode = Schema.AccessMode.READ_ONLY, example = "28.7041")
    @QueryIgnore
    public double getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    private transient double currentLongitude;

    @Schema(description = "Current device longitude (read-only, enriched from device position)",
            accessMode = Schema.AccessMode.READ_ONLY, example = "77.1025")
    @QueryIgnore
    public double getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    private transient double currentSpeed;

    @Schema(description = "Current device speed in knots (read-only, enriched from device position)",
            accessMode = Schema.AccessMode.READ_ONLY, example = "45.5")
    @QueryIgnore
    public double getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(double currentSpeed) {
        this.currentSpeed = currentSpeed;
    }

    private transient Date lastPositionUpdate;

    @Schema(description = "Timestamp of last position update (read-only)",
            accessMode = Schema.AccessMode.READ_ONLY)
    @QueryIgnore
    public Date getLastPositionUpdate() {
        return lastPositionUpdate;
    }

    public void setLastPositionUpdate(Date lastPositionUpdate) {
        this.lastPositionUpdate = lastPositionUpdate;
    }

    // Reference position (first position at trip start)
    private long referencePositionId;

    @Schema(description = "Reference position ID for distance tracking", example = "5001")
    public long getReferencePositionId() {
        return referencePositionId;
    }

    public void setReferencePositionId(long referencePositionId) {
        this.referencePositionId = referencePositionId;
    }

    // Distance Tracking
    private double totalDistance;

    @Schema(description = "Total trip distance in km (auto-calculated using Haversine formula)",
            accessMode = Schema.AccessMode.READ_ONLY, example = "1150.5")
    public double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    private transient double distanceCovered;

    @Schema(description = "Distance covered from origin to current position in km (read-only)",
            accessMode = Schema.AccessMode.READ_ONLY, example = "450.2")
    @QueryIgnore
    public double getDistanceCovered() {
        return distanceCovered;
    }

    public void setDistanceCovered(double distanceCovered) {
        this.distanceCovered = distanceCovered;
    }

    // Waypoints
    private String waypoint1;

    public String getWaypoint1() {
        return waypoint1;
    }

    public void setWaypoint1(String waypoint1) {
        this.waypoint1 = waypoint1;
    }

    private String waypoint2;

    public String getWaypoint2() {
        return waypoint2;
    }

    public void setWaypoint2(String waypoint2) {
        this.waypoint2 = waypoint2;
    }

    // Status Information
    private String tripStatus;

    @Schema(description = "Trip status: pending, running, completed, cancelled",
            example = "running", allowableValues = {"pending", "running", "completed", "cancelled"})
    public String getTripStatus() {
        return tripStatus;
    }

    public void setTripStatus(String tripStatus) {
        this.tripStatus = tripStatus;
    }

    private Date completionDate;

    @QueryIgnore
    @Schema(description = "Actual trip completion date (set when trip is completed)",
            accessMode = Schema.AccessMode.READ_ONLY)
    public Date getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(Date completionDate) {
        this.completionDate = completionDate;
    }

    private Double completionLatitude;

    @QueryIgnore
    @Schema(description = "Final latitude when trip was completed",
            accessMode = Schema.AccessMode.READ_ONLY)
    public Double getCompletionLatitude() {
        return completionLatitude;
    }

    public void setCompletionLatitude(Double completionLatitude) {
        this.completionLatitude = completionLatitude;
    }

    private Double completionLongitude;

    @QueryIgnore
    @Schema(description = "Final longitude when trip was completed",
            accessMode = Schema.AccessMode.READ_ONLY)
    public Double getCompletionLongitude() {
        return completionLongitude;
    }

    public void setCompletionLongitude(Double completionLongitude) {
        this.completionLongitude = completionLongitude;
    }

    private String completionNotes;

    @QueryIgnore
    @Schema(description = "Notes or remarks added when completing the trip",
            example = "Delivered successfully, signed by receiver")
    public String getCompletionNotes() {
        return completionNotes;
    }

    public void setCompletionNotes(String completionNotes) {
        this.completionNotes = completionNotes;
    }

    private Long totalTripTimeMinutes;

    @QueryIgnore
    @Schema(description = "Total trip duration in minutes (from departure to completion)",
            accessMode = Schema.AccessMode.READ_ONLY, example = "1440")
    public Long getTotalTripTimeMinutes() {
        return totalTripTimeMinutes;
    }

    public void setTotalTripTimeMinutes(Long totalTripTimeMinutes) {
        this.totalTripTimeMinutes = totalTripTimeMinutes;
    }

    private String dataDelay;

    public String getDataDelay() {
        return dataDelay;
    }

    public void setDataDelay(String dataDelay) {
        this.dataDelay = dataDelay;
    }

    // Device Information
    private String deviceType;

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    private String ownMarket;

    public String getOwnMarket() {
        return ownMarket;
    }

    public void setOwnMarket(String ownMarket) {
        this.ownMarket = ownMarket;
    }

    // Dates for SDA and DETA
    private Date sda;

    public Date getSda() {
        return sda;
    }

    public void setSda(Date sda) {
        this.sda = sda;
    }

    private Date deta;

    public Date getDeta() {
        return deta;
    }

    public void setDeta(Date deta) {
        this.deta = deta;
    }

    // Metadata
    private Date createdAt;

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    private Date updatedAt;

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

}
