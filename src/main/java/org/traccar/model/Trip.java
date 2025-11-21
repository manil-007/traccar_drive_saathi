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

import org.traccar.storage.StorageName;

import java.util.Date;

@StorageName("tc_trips")
public class Trip extends ExtendedModel {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Asset/Device Information
    private long deviceId;

    public long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(long deviceId) {
        this.deviceId = deviceId;
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

    public long getConsignorId() {
        return consignorId;
    }

    public void setConsignorId(long consignorId) {
        this.consignorId = consignorId;
    }

    private String consignorName;

    public String getConsignorName() {
        return consignorName;
    }

    public void setConsignorName(String consignorName) {
        this.consignorName = consignorName;
    }

    private String otherConsignorName;

    public String getOtherConsignorName() {
        return otherConsignorName;
    }

    public void setOtherConsignorName(String otherConsignorName) {
        this.otherConsignorName = otherConsignorName;
    }

    private long consigneeId;

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
    private String origin;

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    private String destination;

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    // Date and Time Information
    private Date departureDate;

    public Date getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(Date departureDate) {
        this.departureDate = departureDate;
    }

    private Date etaDate;

    public Date getEtaDate() {
        return etaDate;
    }

    public void setEtaDate(Date etaDate) {
        this.etaDate = etaDate;
    }

    private Date bookingDate;

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

    // Location Tracking
    private double latitude;

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    private double longitude;

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    private double speed;

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    // Distance Tracking
    private double totalDistance;

    public double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    private double distanceCovered;

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
    private String runningStatus;

    public String getRunningStatus() {
        return runningStatus;
    }

    public void setRunningStatus(String runningStatus) {
        this.runningStatus = runningStatus;
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
