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
package org.traccar.model;

import org.traccar.storage.StorageName;

import java.util.Date;

@StorageName("tc_driving_license")
public class DrivingLicenseRecord extends BaseModel {

    private long userId;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    private String dlNo; // license number

    public String getDlNo() {
        return dlNo;
    }

    public void setDlNo(String dlNo) {
        this.dlNo = dlNo;
    }

    private String dob; // stored as DD-MM-YYYY

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    private String holderName;

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    private String gender;

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    private String status; // DL status string from API if any

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    private String fatherOrHusbandName;

    public String getFatherOrHusbandName() {
        return fatherOrHusbandName;
    }

    public void setFatherOrHusbandName(String fatherOrHusbandName) {
        this.fatherOrHusbandName = fatherOrHusbandName;
    }

    private String presentAddress;

    public String getPresentAddress() {
        return presentAddress;
    }

    public void setPresentAddress(String presentAddress) {
        this.presentAddress = presentAddress;
    }

    private String permanentAddress;

    public String getPermanentAddress() {
        return permanentAddress;
    }

    public void setPermanentAddress(String permanentAddress) {
        this.permanentAddress = permanentAddress;
    }

    private String issueDate; // keep as string as API uses DD-MM-YYYY

    public String getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(String issueDate) {
        this.issueDate = issueDate;
    }

    private String rtoCode;

    public String getRtoCode() {
        return rtoCode;
    }

    public void setRtoCode(String rtoCode) {
        this.rtoCode = rtoCode;
    }

    // Additional location info
    private String rto; // e.g., SIKAR

    public String getRto() {
        return rto;
    }

    public void setRto(String rto) {
        this.rto = rto;
    }

    private String state; // e.g., RAJASTHAN

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    // validity windows
    private Date nonTransportValidFrom;

    public Date getNonTransportValidFrom() {
        return nonTransportValidFrom;
    }

    public void setNonTransportValidFrom(Date nonTransportValidFrom) {
        this.nonTransportValidFrom = nonTransportValidFrom;
    }

    private Date nonTransportValidTo;

    public Date getNonTransportValidTo() {
        return nonTransportValidTo;
    }

    public void setNonTransportValidTo(Date nonTransportValidTo) {
        this.nonTransportValidTo = nonTransportValidTo;
    }

    private Date transportValidFrom;

    public Date getTransportValidFrom() {
        return transportValidFrom;
    }

    public void setTransportValidFrom(Date transportValidFrom) {
        this.transportValidFrom = transportValidFrom;
    }

    private Date transportValidTo;

    public Date getTransportValidTo() {
        return transportValidTo;
    }

    public void setTransportValidTo(Date transportValidTo) {
        this.transportValidTo = transportValidTo;
    }

    private String vehicleClassJson; // store array as JSON string

    public String getVehicleClassJson() {
        return vehicleClassJson;
    }

    public void setVehicleClassJson(String vehicleClassJson) {
        this.vehicleClassJson = vehicleClassJson;
    }

    // Validity simple window (fallback when split validity is absent)
    private Date validFrom;

    public Date getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Date validFrom) {
        this.validFrom = validFrom;
    }

    private Date validUpto;

    public Date getValidUpto() {
        return validUpto;
    }

    public void setValidUpto(Date validUpto) {
        this.validUpto = validUpto;
    }

    private String image; // base64 or token returned by API

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    private String bloodGroup;

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    private String rawResponse; // entire upstream response JSON

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    // computed
    private String nonTransportExpiryStatus; // valid/expired/unknown

    public String getNonTransportExpiryStatus() {
        return nonTransportExpiryStatus;
    }

    public void setNonTransportExpiryStatus(String nonTransportExpiryStatus) {
        this.nonTransportExpiryStatus = nonTransportExpiryStatus;
    }

    private String transportExpiryStatus; // valid/expired/unknown

    public String getTransportExpiryStatus() {
        return transportExpiryStatus;
    }

    public void setTransportExpiryStatus(String transportExpiryStatus) {
        this.transportExpiryStatus = transportExpiryStatus;
    }

    private String overallExpiryStatus; // valid/expired/unknown

    public String getOverallExpiryStatus() {
        return overallExpiryStatus;
    }

    public void setOverallExpiryStatus(String overallExpiryStatus) {
        this.overallExpiryStatus = overallExpiryStatus;
    }

    private Date overallExpiryDate; // max of non/transport validTo

    public Date getOverallExpiryDate() {
        return overallExpiryDate;
    }

    public void setOverallExpiryDate(Date overallExpiryDate) {
        this.overallExpiryDate = overallExpiryDate;
    }

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
