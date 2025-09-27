package org.traccar.api.resource;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.storage.Storage;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Path("/vehicleData")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VehicleDataResource {

        @Inject
        private Storage storage;

        public static class VehicleDataRequest {
                @JsonProperty("company_names")
                private String companyNames;
                @JsonProperty("vehicle_nos")
                private String vehicleNos;
                @JsonProperty("imei_nos")
                private String imeiNos;

                public String getCompanyNames() {
                        return companyNames;
                }

                public void setCompanyNames(String companyNames) {
                        this.companyNames = companyNames;
                }

                public String getVehicleNos() {
                        return vehicleNos;
                }

                public void setVehicleNos(String vehicleNos) {
                        this.vehicleNos = vehicleNos;
                }

                public String getImeiNos() {
                        return imeiNos;
                }

                public void setImeiNos(String imeiNos) {
                        this.imeiNos = imeiNos;
                }
        }

        public static class VehicleDataResponse {
                private Root root = new Root();

                public Root getRoot() {
                        return root;
                }

                public void setRoot(Root root) {
                        this.root = root;
                }

                public static class Root {
                        @JsonProperty("VehicleData")
                        private List<VehicleData> vehicleData = new ArrayList<>();

                        public List<VehicleData> getVehicleData() {
                                return vehicleData;
                        }

                        public void setVehicleData(List<VehicleData> vehicleData) {
                                this.vehicleData = vehicleData;
                        }
                }

                public static class VehicleData {
                        @JsonProperty("Vehicle_Name")
                        private String vehicleName = "";
                        @JsonProperty("Company")
                        private String company = "";
                        @JsonProperty("Temperature")
                        private String temperature = "";
                        @JsonProperty("heartbeat")
                        private String heartbeat = "";
                        @JsonProperty("Latitude")
                        private String latitude = "";
                        @JsonProperty("GPS")
                        private String gps = "";
                        @JsonProperty("mcc")
                        private String mcc = "";
                        @JsonProperty("cellid")
                        private String cellid = "";
                        @JsonProperty("lac")
                        private String lac = "";
                        @JsonProperty("Vehicle_No")
                        private String vehicleNo = "";
                        @JsonProperty("Door1")
                        private String door1 = "";
                        @JsonProperty("elock")
                        private String elock = "";
                        @JsonProperty("Door4")
                        private String door4 = "";
                        @JsonProperty("Branch")
                        private String branch = "";
                        @JsonProperty("Vehicletype")
                        private String vehicleType = "";
                        @JsonProperty("Door2")
                        private String door2 = "";
                        @JsonProperty("Door3")
                        private String door3 = "";
                        @JsonProperty("course")
                        private String course = "";
                        @JsonProperty("GPSActualTime")
                        private String gpsActualTime = "";
                        @JsonProperty("Datetime")
                        private String datetime = "";
                        @JsonProperty("Status")
                        private String status = "";
                        @JsonProperty("DeviceModel")
                        private String deviceModel = "";
                        @JsonProperty("Speed")
                        private String speed = "";
                        @JsonProperty("AC")
                        private String ac = "";
                        @JsonProperty("mnc")
                        private String mnc = "";
                        @JsonProperty("Imeino")
                        private String imeino = "";
                        @JsonProperty("gps_hdop")
                        private String gpsHdop = "";
                        @JsonProperty("Odometer")
                        private String odometer = "";
                        @JsonProperty("POI")
                        private String poi = "";
                        @JsonProperty("Driver_Middle_Name")
                        private String driverMiddleName = "";
                        @JsonProperty("Longitude")
                        private String longitude = "";
                        @JsonProperty("Immobilize_State")
                        private String immobilizeState = "";
                        @JsonProperty("IGN")
                        private String ign = "";
                        @JsonProperty("satellite_count")
                        private Integer satelliteCount = 0;
                        @JsonProperty("Driver_First_Name")
                        private String driverFirstName = "";
                        @JsonProperty("Angle")
                        private String angle = "";
                        @JsonProperty("Ibutton/RFID")
                        private String ibuttonRfid = "";
                        @JsonProperty("SOS")
                        private String sos = "";
                        @JsonProperty("Fuel")
                        private List<String> fuel = new ArrayList<>();
                        @JsonProperty("battery_percentage")
                        private Integer batteryPercentage = 0;
                        @JsonProperty("ExternalVolt")
                        private String externalVolt = "";
                        @JsonProperty("Driver_Last_Name")
                        private String driverLastName = "";
                        @JsonProperty("Power")
                        private String power = "";
                        @JsonProperty("username")
                        private String username = "";
                        @JsonProperty("Location")
                        private String location = "";
                        @JsonProperty("Altitude")
                        private String altitude = "";

                        // Getters and setters for all fields
                        public String getVehicleName() {
                                return vehicleName;
                        }

                        public void setVehicleName(String vehicleName) {
                                this.vehicleName = vehicleName;
                        }

                        public String getCompany() {
                                return company;
                        }

                        public void setCompany(String company) {
                                this.company = company;
                        }

                        public String getTemperature() {
                                return temperature;
                        }

                        public void setTemperature(String temperature) {
                                this.temperature = temperature;
                        }

                        public String getHeartbeat() {
                                return heartbeat;
                        }

                        public void setHeartbeat(String heartbeat) {
                                this.heartbeat = heartbeat;
                        }

                        public String getLatitude() {
                                return latitude;
                        }

                        public void setLatitude(String latitude) {
                                this.latitude = latitude;
                        }

                        public String getGps() {
                                return gps;
                        }

                        public void setGps(String gps) {
                                this.gps = gps;
                        }

                        public String getMcc() {
                                return mcc;
                        }

                        public void setMcc(String mcc) {
                                this.mcc = mcc;
                        }

                        public String getCellid() {
                                return cellid;
                        }

                        public void setCellid(String cellid) {
                                this.cellid = cellid;
                        }

                        public String getLac() {
                                return lac;
                        }

                        public void setLac(String lac) {
                                this.lac = lac;
                        }

                        public String getVehicleNo() {
                                return vehicleNo;
                        }

                        public void setVehicleNo(String vehicleNo) {
                                this.vehicleNo = vehicleNo;
                        }

                        public String getDoor1() {
                                return door1;
                        }

                        public void setDoor1(String door1) {
                                this.door1 = door1;
                        }

                        public String getElock() {
                                return elock;
                        }

                        public void setElock(String elock) {
                                this.elock = elock;
                        }

                        public String getDoor4() {
                                return door4;
                        }

                        public void setDoor4(String door4) {
                                this.door4 = door4;
                        }

                        public String getBranch() {
                                return branch;
                        }

                        public void setBranch(String branch) {
                                this.branch = branch;
                        }

                        public String getVehicleType() {
                                return vehicleType;
                        }

                        public void setVehicleType(String vehicleType) {
                                this.vehicleType = vehicleType;
                        }

                        public String getDoor2() {
                                return door2;
                        }

                        public void setDoor2(String door2) {
                                this.door2 = door2;
                        }

                        public String getDoor3() {
                                return door3;
                        }

                        public void setDoor3(String door3) {
                                this.door3 = door3;
                        }

                        public String getCourse() {
                                return course;
                        }

                        public void setCourse(String course) {
                                this.course = course;
                        }

                        public String getGpsActualTime() {
                                return gpsActualTime;
                        }

                        public void setGpsActualTime(String gpsActualTime) {
                                this.gpsActualTime = gpsActualTime;
                        }

                        public String getDatetime() {
                                return datetime;
                        }

                        public void setDatetime(String datetime) {
                                this.datetime = datetime;
                        }

                        public String getStatus() {
                                return status;
                        }

                        public void setStatus(String status) {
                                this.status = status;
                        }

                        public String getDeviceModel() {
                                return deviceModel;
                        }

                        public void setDeviceModel(String deviceModel) {
                                this.deviceModel = deviceModel;
                        }

                        public String getSpeed() {
                                return speed;
                        }

                        public void setSpeed(String speed) {
                                this.speed = speed;
                        }

                        public String getAc() {
                                return ac;
                        }

                        public void setAc(String ac) {
                                this.ac = ac;
                        }

                        public String getMnc() {
                                return mnc;
                        }

                        public void setMnc(String mnc) {
                                this.mnc = mnc;
                        }

                        public String getImeino() {
                                return imeino;
                        }

                        public void setImeino(String imeino) {
                                this.imeino = imeino;
                        }

                        public String getGpsHdop() {
                                return gpsHdop;
                        }

                        public void setGpsHdop(String gpsHdop) {
                                this.gpsHdop = gpsHdop;
                        }

                        public String getOdometer() {
                                return odometer;
                        }

                        public void setOdometer(String odometer) {
                                this.odometer = odometer;
                        }

                        public String getPoi() {
                                return poi;
                        }

                        public void setPoi(String poi) {
                                this.poi = poi;
                        }

                        public String getDriverMiddleName() {
                                return driverMiddleName;
                        }

                        public void setDriverMiddleName(String driverMiddleName) {
                                this.driverMiddleName = driverMiddleName;
                        }

                        public String getLongitude() {
                                return longitude;
                        }

                        public void setLongitude(String longitude) {
                                this.longitude = longitude;
                        }

                        public String getImmobilizeState() {
                                return immobilizeState;
                        }

                        public void setImmobilizeState(String immobilizeState) {
                                this.immobilizeState = immobilizeState;
                        }

                        public String getIgn() {
                                return ign;
                        }

                        public void setIgn(String ign) {
                                this.ign = ign;
                        }

                        public Integer getSatelliteCount() {
                                return satelliteCount;
                        }

                        public void setSatelliteCount(Integer satelliteCount) {
                                this.satelliteCount = satelliteCount;
                        }

                        public String getDriverFirstName() {
                                return driverFirstName;
                        }

                        public void setDriverFirstName(String driverFirstName) {
                                this.driverFirstName = driverFirstName;
                        }

                        public String getAngle() {
                                return angle;
                        }

                        public void setAngle(String angle) {
                                this.angle = angle;
                        }

                        public String getIbuttonRfid() {
                                return ibuttonRfid;
                        }

                        public void setIbuttonRfid(String ibuttonRfid) {
                                this.ibuttonRfid = ibuttonRfid;
                        }

                        public String getSos() {
                                return sos;
                        }

                        public void setSos(String sos) {
                                this.sos = sos;
                        }

                        public List<String> getFuel() {
                                return fuel;
                        }

                        public void setFuel(List<String> fuel) {
                                this.fuel = fuel;
                        }

                        public Integer getBatteryPercentage() {
                                return batteryPercentage;
                        }

                        public void setBatteryPercentage(Integer batteryPercentage) {
                                this.batteryPercentage = batteryPercentage;
                        }

                        public String getExternalVolt() {
                                return externalVolt;
                        }

                        public void setExternalVolt(String externalVolt) {
                                this.externalVolt = externalVolt;
                        }

                        public String getDriverLastName() {
                                return driverLastName;
                        }

                        public void setDriverLastName(String driverLastName) {
                                this.driverLastName = driverLastName;
                        }

                        public String getPower() {
                                return power;
                        }

                        public void setPower(String power) {
                                this.power = power;
                        }

                        public String getUsername() {
                                return username;
                        }

                        public void setUsername(String username) {
                                this.username = username;
                        }

                        public String getLocation() {
                                return location;
                        }

                        public void setLocation(String location) {
                                this.location = location;
                        }

                        public String getAltitude() {
                                return altitude;
                        }

                        public void setAltitude(String altitude) {
                                this.altitude = altitude;
                        }
                }
        }

        @PermitAll
        @POST
        public Response getVehicleData(@Context HttpHeaders headers, VehicleDataRequest request) throws Exception {
                // 1. Get token from header
                String token = headers.getHeaderString("auth-code");
                if (token == null || token.trim().isEmpty()) {
                        return Response.status(Response.Status.UNAUTHORIZED).entity("Missing auth-code header.")
                                        .build();
                }

                // 2. Validate token
                String cookie = GenerateAccessTokenResource.getCookieForToken(token);
                if (cookie == null) {
                        return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid or expired token.")
                                        .build();
                }
                VehicleDataResponse response = new VehicleDataResponse();

                Set<String> imeiSet = new HashSet<>();
                Set<String> vehicleNoSet = new HashSet<>();
                boolean filterByCompany = request.getCompanyNames() != null && !request.getCompanyNames().isEmpty();
                boolean filterByImei = request.getImeiNos() != null && !request.getImeiNos().isEmpty();
                boolean filterByVehicleNo = request.getVehicleNos() != null && !request.getVehicleNos().isEmpty();

                if (filterByImei) {
                        for (String imei : request.getImeiNos().split(",")) {
                                imeiSet.add(imei.trim());
                        }
                }
                if (filterByVehicleNo) {
                        for (String vno : request.getVehicleNos().split(",")) {
                                vehicleNoSet.add(vno.trim());
                        }
                }
                List<Device> devices = storage.getObjects(Device.class,
                                new Request(new org.traccar.storage.query.Columns.All()));

                for (Device device : devices) {
                        boolean matches = false;
                        String vehicleNo = device.getAttributes() != null
                                        && device.getAttributes().get("vehicleNo") != null
                                                        ? device.getAttributes().get("vehicleNo").toString()
                                                        : null;
                        // If only company name is provided
                        if (filterByCompany && !filterByImei && !filterByVehicleNo) {
                                String company = device.getAttributes() != null
                                                && device.getAttributes().get("company") != null
                                                                ? device.getAttributes().get("company").toString()
                                                                : null;
                                if (request.getCompanyNames().equalsIgnoreCase(company)) {
                                        matches = true;
                                }
                        } else if (filterByImei || filterByVehicleNo) {
                                // If IMEI or vehicle no is provided
                                if (filterByImei && imeiSet.contains(device.getUniqueId())) {
                                        matches = true;
                                }

                                if (filterByVehicleNo && vehicleNo != null && vehicleNoSet.contains(vehicleNo)) {
                                        matches = true;
                                }
                        } else if (!filterByCompany && !filterByImei && !filterByVehicleNo) {
                                // If no filters are provided, include all devices
                                matches = true;
                        }

                        if (!matches) {
                                continue;
                        }

                        Position position = null;
                        if (device.getPositionId() != 0) {
                                Condition posCond = new Condition.Equals("id", device.getPositionId());
                                Request posReq = new Request(new org.traccar.storage.query.Columns.All(), posCond);
                                position = storage.getObject(Position.class, posReq);
                        }
                        VehicleDataResponse.VehicleData vehicle = new VehicleDataResponse.VehicleData();
                        vehicle.setVehicleName(device.getName());
                        vehicle.setCompany(device.getAttributes() != null
                                        && device.getAttributes().get("Company") != null
                                                        ? device.getAttributes().get("Company").toString()
                                                        : "--");

                        vehicle.setVehicleNo(device.getAttributes() != null
                                        && device.getAttributes().get("vehicleNo") != null
                                                        ? device.getAttributes().get("vehicleNo").toString()
                                                        : "");

                        vehicle.setImeino(device.getUniqueId());

                        vehicle.setDeviceModel(device.getModel() != null ? device.getModel() : "");

                        vehicle.setStatus(device.getStatus() != null ? device.getStatus() : "");

                        vehicle.setUsername(device.getAttributes() != null
                                        && device.getAttributes().get("username") != null
                                                        ? device.getAttributes().get("username").toString()
                                                        : "");

                        vehicle.setDatetime(device.getLastUpdate() != null
                                        ? new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
                                                        .format(device.getLastUpdate())
                                        : "");

                        // Position-based fields
                        if (position != null) {
                                vehicle.setLatitude(String.valueOf(position.getLatitude()));
                                vehicle.setLongitude(String.valueOf(position.getLongitude()));
                                vehicle.setSpeed(position.getSpeed() <= 0.01
                                                ? String.valueOf(Math.round(position.getSpeed() * 1.852))
                                                : "0");
                                vehicle.setGpsActualTime(position.getFixTime() != null
                                                ? new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss")
                                                                .format(position.getFixTime())
                                                : "");
                                vehicle.setAngle(String.valueOf((int) position.getCourse()));
                                vehicle.setAltitude(String.valueOf(position.getAltitude()));

                                vehicle.setSatelliteCount(position.getAttributes() != null && position.getAttributes()
                                                .get(Position.KEY_SATELLITES) instanceof Number
                                                                ? ((Number) position.getAttributes()
                                                                                .get(Position.KEY_SATELLITES))
                                                                                .intValue()
                                                                : 0);

                                vehicle.setGpsHdop(position.getAttributes() != null
                                                && position.getAttributes().get(Position.KEY_HDOP) != null
                                                                ? position.getAttributes().get(Position.KEY_HDOP)
                                                                                .toString()
                                                                : "NA");

                                vehicle.setOdometer(position.getAttributes() != null
                                                && position.getAttributes().get(Position.KEY_ODOMETER) != null
                                                                ? position.getAttributes().get(Position.KEY_ODOMETER)
                                                                                .toString()
                                                                : "0");

                                vehicle.setLocation(position.getAddress() != null ? position.getAddress() : "");

                                vehicle.setAc(position.getAttributes() != null
                                                && position.getAttributes().get("ac") != null
                                                                ? position.getAttributes().get("ac").toString()
                                                                : "--");
                                vehicle.setPower(position.getAttributes() != null
                                                && position.getAttributes().get(Position.KEY_POWER) != null
                                                                ? position.getAttributes().get(Position.KEY_POWER)
                                                                                .toString()
                                                                : "--");
                                vehicle.setExternalVolt(position.getAttributes() != null
                                                && position.getAttributes().get(Position.KEY_BATTERY) != null
                                                                ? position.getAttributes().get(Position.KEY_BATTERY)
                                                                                .toString()
                                                                : "--");

                                vehicle.setBatteryPercentage(position.getAttributes() != null
                                                && position.getAttributes().get(Position.KEY_BATTERY_LEVEL) != null
                                                                ? ((Number) position.getAttributes()
                                                                                .get(Position.KEY_BATTERY_LEVEL))
                                                                                .intValue()
                                                                : 0);

                                vehicle.setTemperature(position.getAttributes() != null
                                                && position.getAttributes().get(Position.KEY_DEVICE_TEMP) != null
                                                                ? position.getAttributes().get(Position.KEY_DEVICE_TEMP)
                                                                                .toString()
                                                                : "--");
                                vehicle.setHeartbeat(position.getAttributes().get("heartbeat") != null
                                                ? position.getAttributes().get("heartbeat").toString()
                                                : "no");
                                vehicle.setDoor1(position.getAttributes().get("door1") != null
                                                ? position.getAttributes().get("door1").toString()
                                                : "--");
                                vehicle.setDoor2(position.getAttributes().get("door2") != null
                                                ? position.getAttributes().get("door2").toString()
                                                : "--");
                                vehicle.setDoor3(position.getAttributes().get("door3") != null
                                                ? position.getAttributes().get("door3").toString()
                                                : "--");
                                vehicle.setDoor4(position.getAttributes().get("door4") != null
                                                ? position.getAttributes().get("door4").toString()
                                                : "--");
                                vehicle.setElock(position.getAttributes().get("elock") != null
                                                ? position.getAttributes().get("elock").toString()
                                                : "--");
                                vehicle.setImmobilizeState(device.getAttributes() != null
                                                && device.getAttributes().get("immobilizeState") != null
                                                                ? device.getAttributes().get("immobilizeState")
                                                                                .toString()
                                                                : "--");
                                vehicle.setIgn(position.getAttributes() != null
                                                && position.getAttributes().get(Position.KEY_IGNITION) != null
                                                                ? position.getAttributes().get(Position.KEY_IGNITION)
                                                                                .toString()
                                                                : "--");

                                vehicle.setSos(position.getAttributes() != null
                                                && position.getAttributes().get(Position.KEY_ALARM) != null
                                                && position.getAttributes().get(Position.KEY_ALARM).toString()
                                                                .contains(Position.ALARM_SOS)
                                                                                ? Position.ALARM_SOS
                                                                                : "--");
                                vehicle.setPoi(position.getAttributes() != null
                                                && position.getAttributes().get("poi") != null
                                                                ? position.getAttributes().get("poi").toString()
                                                                : "--");
                                Object fuelObj = position.getAttributes().get("fuel");
                                if (fuelObj instanceof List<?>) {
                                        @SuppressWarnings("unchecked")
                                        List<String> fuelList = (List<String>) fuelObj;
                                        vehicle.setFuel(fuelList);
                                } else if (fuelObj != null) {
                                        List<String> fuelList = new ArrayList<>();
                                        fuelList.add(fuelObj.toString());
                                        vehicle.setFuel(fuelList);
                                } else {
                                        vehicle.setFuel(new ArrayList<>());
                                }
                                vehicle.setMcc(position.getNetwork() != null
                                                && position.getNetwork().getCellTowers() != null
                                                && !position.getNetwork().getCellTowers().isEmpty()
                                                                ? String.valueOf(position.getNetwork().getCellTowers()
                                                                                .iterator().next()
                                                                                .getMobileCountryCode())
                                                                : "--");
                                vehicle.setMnc(position.getNetwork() != null
                                                && position.getNetwork().getCellTowers() != null
                                                && !position.getNetwork().getCellTowers().isEmpty()
                                                                ? String.valueOf(position.getNetwork().getCellTowers()
                                                                                .iterator().next()
                                                                                .getMobileNetworkCode())
                                                                : "--");
                                vehicle.setCellid(position.getNetwork() != null
                                                && position.getNetwork().getCellTowers() != null
                                                && !position.getNetwork().getCellTowers().isEmpty()
                                                                ? String.valueOf(position.getNetwork().getCellTowers()
                                                                                .iterator().next().getCellId())
                                                                : "NA");
                                vehicle.setLac(position.getNetwork() != null
                                                && position.getNetwork().getCellTowers() != null
                                                && !position.getNetwork().getCellTowers().isEmpty()
                                                                ? String.valueOf(position.getNetwork().getCellTowers()
                                                                                .iterator().next()
                                                                                .getLocationAreaCode())
                                                                : "");
                        }

                        // Other attributes (if available)
                        vehicle.setBranch(device.getAttributes() != null && device.getAttributes().get("branch") != null
                                        ? device.getAttributes().get("branch").toString()
                                        : "");
                        vehicle.setVehicleType(device.getAttributes() != null
                                        && device.getAttributes().get("vehicleType") != null
                                                        ? device.getAttributes().get("vehicleType").toString()
                                                        : "");
                        vehicle.setDriverFirstName(device.getAttributes() != null
                                        && device.getAttributes().get("driverFirstName") != null
                                                        ? device.getAttributes().get("driverFirstName").toString()
                                                        : "--");
                        vehicle.setDriverMiddleName(device.getAttributes() != null
                                        && device.getAttributes().get("driverMiddleName") != null
                                                        ? device.getAttributes().get("driverMiddleName").toString()
                                                        : "--");
                        vehicle.setDriverLastName(device.getAttributes() != null
                                        && device.getAttributes().get("driverLastName") != null
                                                        ? device.getAttributes().get("driverLastName").toString()
                                                        : "--");
                        vehicle.setIbuttonRfid(device.getAttributes() != null
                                        && device.getAttributes().get("ibutton") != null
                                                        ? device.getAttributes().get("ibutton").toString()
                                                        : "--");

                        response.getRoot().getVehicleData().add(vehicle);
                }

                return Response.ok(response).build();
        }
}
