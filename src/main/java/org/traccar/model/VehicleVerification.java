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

@StorageName("tc_vehicle_validity")
public class VehicleVerification extends BaseModel {
    private long userId;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    private String licensePlate;

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    private String chassisNumber;

    public String getChassisNumber() {
        return chassisNumber;
    }

    public void setChassisNumber(String chassisNumber) {
        this.chassisNumber = chassisNumber;
    }

    private String engineNumber;

    public String getEngineNumber() {
        return engineNumber;
    }

    public void setEngineNumber(String engineNumber) {
        this.engineNumber = engineNumber;
    }

    private String ownerName;

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    private String fatherName;

    public String getFatherName() {
        return fatherName;
    }

    public void setFatherName(String fatherName) {
        this.fatherName = fatherName;
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

    private String isFinanced;

    public String getIsFinanced() {
        return isFinanced;
    }

    public void setIsFinanced(String isFinanced) {
        this.isFinanced = isFinanced;
    }

    private String financer;

    public String getFinancer() {
        return financer;
    }

    public void setFinancer(String financer) {
        this.financer = financer;
    }

    private String insuranceCompany;

    public String getInsuranceCompany() {
        return insuranceCompany;
    }

    public void setInsuranceCompany(String insuranceCompany) {
        this.insuranceCompany = insuranceCompany;
    }

    private String insurancePolicy;

    public String getInsurancePolicy() {
        return insurancePolicy;
    }

    public void setInsurancePolicy(String insurancePolicy) {
        this.insurancePolicy = insurancePolicy;
    }

    private Date insuranceExpiry;

    public Date getInsuranceExpiry() {
        return insuranceExpiry;
    }

    public void setInsuranceExpiry(Date insuranceExpiry) {
        this.insuranceExpiry = insuranceExpiry;
    }

    private String vehicleClass;

    public String getVehicleClass() {
        return vehicleClass;
    }

    public void setVehicleClass(String vehicleClass) {
        this.vehicleClass = vehicleClass;
    }

    private Date registrationDate;

    public Date getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(Date registrationDate) {
        this.registrationDate = registrationDate;
    }

    private String vehicleAge;

    public String getVehicleAge() {
        return vehicleAge;
    }

    public void setVehicleAge(String vehicleAge) {
        this.vehicleAge = vehicleAge;
    }

    private Date puccUpto;

    public Date getPuccUpto() {
        return puccUpto;
    }

    public void setPuccUpto(Date puccUpto) {
        this.puccUpto = puccUpto;
    }

    private String puccNumber;

    public String getPuccNumber() {
        return puccNumber;
    }

    public void setPuccNumber(String puccNumber) {
        this.puccNumber = puccNumber;
    }

    private String fuelType;

    public String getFuelType() {
        return fuelType;
    }

    public void setFuelType(String fuelType) {
        this.fuelType = fuelType;
    }

    private String brandName;

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    private String brandModel;

    public String getBrandModel() {
        return brandModel;
    }

    public void setBrandModel(String brandModel) {
        this.brandModel = brandModel;
    }

    private String cubicCapacity;

    public String getCubicCapacity() {
        return cubicCapacity;
    }

    public void setCubicCapacity(String cubicCapacity) {
        this.cubicCapacity = cubicCapacity;
    }

    private String grossWeight;

    public String getGrossWeight() {
        return grossWeight;
    }

    public void setGrossWeight(String grossWeight) {
        this.grossWeight = grossWeight;
    }

    private String cylinders;

    public String getCylinders() {
        return cylinders;
    }

    public void setCylinders(String cylinders) {
        this.cylinders = cylinders;
    }

    private String color;

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    private String norms;

    public String getNorms() {
        return norms;
    }

    public void setNorms(String norms) {
        this.norms = norms;
    }

    private String nocDetails;

    public String getNocDetails() {
        return nocDetails;
    }

    public void setNocDetails(String nocDetails) {
        this.nocDetails = nocDetails;
    }

    private String seatingCapacity;

    public String getSeatingCapacity() {
        return seatingCapacity;
    }

    public void setSeatingCapacity(String seatingCapacity) {
        this.seatingCapacity = seatingCapacity;
    }

    private String ownerCount;

    public String getOwnerCount() {
        return ownerCount;
    }

    public void setOwnerCount(String ownerCount) {
        this.ownerCount = ownerCount;
    }

    private Date taxUpto;

    public Date getTaxUpto() {
        return taxUpto;
    }

    public void setTaxUpto(Date taxUpto) {
        this.taxUpto = taxUpto;
    }

    private String taxPaidUpto;

    public String getTaxPaidUpto() {
        return taxPaidUpto;
    }

    public void setTaxPaidUpto(String taxPaidUpto) {
        this.taxPaidUpto = taxPaidUpto;
    }

    private String permitNumber;

    public String getPermitNumber() {
        return permitNumber;
    }

    public void setPermitNumber(String permitNumber) {
        this.permitNumber = permitNumber;
    }

    private Date permitIssueDate;

    public Date getPermitIssueDate() {
        return permitIssueDate;
    }

    public void setPermitIssueDate(Date permitIssueDate) {
        this.permitIssueDate = permitIssueDate;
    }

    private Date permitValidFrom;

    public Date getPermitValidFrom() {
        return permitValidFrom;
    }

    public void setPermitValidFrom(Date permitValidFrom) {
        this.permitValidFrom = permitValidFrom;
    }

    private Date permitValidUpto;

    public Date getPermitValidUpto() {
        return permitValidUpto;
    }

    public void setPermitValidUpto(Date permitValidUpto) {
        this.permitValidUpto = permitValidUpto;
    }

    private String permitType;

    public String getPermitType() {
        return permitType;
    }

    public void setPermitType(String permitType) {
        this.permitType = permitType;
    }

    private String nationalPermitNumber;

    public String getNationalPermitNumber() {
        return nationalPermitNumber;
    }

    public void setNationalPermitNumber(String nationalPermitNumber) {
        this.nationalPermitNumber = nationalPermitNumber;
    }

    private Date nationalPermitUpto;

    public Date getNationalPermitUpto() {
        return nationalPermitUpto;
    }

    public void setNationalPermitUpto(Date nationalPermitUpto) {
        this.nationalPermitUpto = nationalPermitUpto;
    }

    private String nationalPermitIssuedBy;

    public String getNationalPermitIssuedBy() {
        return nationalPermitIssuedBy;
    }

    public void setNationalPermitIssuedBy(String nationalPermitIssuedBy) {
        this.nationalPermitIssuedBy = nationalPermitIssuedBy;
    }

    private String rcStatus;

    public String getRcStatus() {
        return rcStatus;
    }

    public void setRcStatus(String rcStatus) {
        this.rcStatus = rcStatus;
    }

    private String category;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    private String bodyType;

    public String getBodyType() {
        return bodyType;
    }

    public void setBodyType(String bodyType) {
        this.bodyType = bodyType;
    }

    private Date fitUpTo;

    public Date getFitUpTo() {
        return fitUpTo;
    }

    public void setFitUpTo(Date fitUpTo) {
        this.fitUpTo = fitUpTo;
    }

    private String manufacturingDate;

    public String getManufacturingDate() {
        return manufacturingDate;
    }

    public void setManufacturingDate(String manufacturingDate) {
        this.manufacturingDate = manufacturingDate;
    }

    private String manufacturingDateFormatted;

    public String getManufacturingDateFormatted() {
        return manufacturingDateFormatted;
    }

    public void setManufacturingDateFormatted(String manufacturingDateFormatted) {
        this.manufacturingDateFormatted = manufacturingDateFormatted;
    }

    private String rtoName;

    public String getRtoName() {
        return rtoName;
    }

    public void setRtoName(String rtoName) {
        this.rtoName = rtoName;
    }

    private String latestBy;

    public String getLatestBy() {
        return latestBy;
    }

    public void setLatestBy(String latestBy) {
        this.latestBy = latestBy;
    }

    private String sleeperCapacity;

    public String getSleeperCapacity() {
        return sleeperCapacity;
    }

    public void setSleeperCapacity(String sleeperCapacity) {
        this.sleeperCapacity = sleeperCapacity;
    }

    private String standingCapacity;

    public String getStandingCapacity() {
        return standingCapacity;
    }

    public void setStandingCapacity(String standingCapacity) {
        this.standingCapacity = standingCapacity;
    }

    private String wheelbase;

    public String getWheelbase() {
        return wheelbase;
    }

    public void setWheelbase(String wheelbase) {
        this.wheelbase = wheelbase;
    }

    private String unladenWeight;

    public String getUnladenWeight() {
        return unladenWeight;
    }

    public void setUnladenWeight(String unladenWeight) {
        this.unladenWeight = unladenWeight;
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

    private String overallValidityStatus; // valid/invalid based on critical fields

    public String getOverallValidityStatus() {
        return overallValidityStatus;
    }

    public void setOverallValidityStatus(String overallValidityStatus) {
        this.overallValidityStatus = overallValidityStatus;
    }
}
