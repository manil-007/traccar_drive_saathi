package org.traccar.api.resource;

import com.fasterxml.jackson.annotation.JsonProperty;

// import org.traccar.Context;
// import org.traccar.BasePipelineFactory;
import org.traccar.model.Device;
import org.traccar.model.Position;
// import org.traccar.storage.QueryBuilder;
import org.traccar.storage.Storage;

/*changes start*/
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import jakarta.ws.rs.*;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.*;

@Path("/vehicleData")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VehicleDataResource {
        /* changes start */

        @Inject
        private Storage storage;
        /* changes end */

        public static class VehicleDataRequest {
                public String company_names;
                public String vehicle_nos;
                public String imei_nos;
        }

        public static class VehicleDataResponse {
                public Root root = new Root();

                public static class Root {
                        @JsonProperty("VehicleData")
                        public List<VehicleData> vehicleData = new ArrayList<>();
                }

                public static class VehicleData {
                        @JsonProperty("Vehicle_Name")
                        public String vehicleName = "";
                        @JsonProperty("Company")
                        public String company = "";
                        @JsonProperty("Temperature")
                        public String temperature = "";
                        @JsonProperty("heartbeat")
                        public String heartbeat = "";
                        @JsonProperty("Latitude")
                        public String latitude = "";
                        @JsonProperty("GPS")
                        public String gps = "";
                        @JsonProperty("mcc")
                        public String mcc = "";
                        @JsonProperty("cellid")
                        public String cellid = "";
                        @JsonProperty("lac")
                        public String lac = "";
                        @JsonProperty("Vehicle_No")
                        public String vehicleNo = "";
                        @JsonProperty("Door1")
                        public String door1 = "";
                        @JsonProperty("elock")
                        public String elock = "";
                        @JsonProperty("Door4")
                        public String door4 = "";
                        @JsonProperty("Branch")
                        public String branch = "";
                        @JsonProperty("Vehicletype")
                        public String vehicleType = "";
                        @JsonProperty("Door2")
                        public String door2 = "";
                        @JsonProperty("Door3")
                        public String door3 = "";
                        @JsonProperty("course")
                        public String course = "";
                        @JsonProperty("GPSActualTime")
                        public String gpsActualTime = "";
                        @JsonProperty("Datetime")
                        public String datetime = "";
                        @JsonProperty("Status")
                        public String status = "";
                        @JsonProperty("DeviceModel")
                        public String deviceModel = "";
                        @JsonProperty("Speed")
                        public String speed = "";
                        @JsonProperty("AC")
                        public String ac = "";
                        @JsonProperty("mnc")
                        public String mnc = "";
                        @JsonProperty("Imeino")
                        public String imeino = "";
                        @JsonProperty("gps_hdop")
                        public String gpsHdop = "";
                        @JsonProperty("Odometer")
                        public String odometer = "";
                        @JsonProperty("POI")
                        public String poi = "";
                        @JsonProperty("Driver_Middle_Name")
                        public String driverMiddleName = "";
                        @JsonProperty("Longitude")
                        public String longitude = "";
                        @JsonProperty("Immobilize_State")
                        public String immobilizeState = "";
                        @JsonProperty("IGN")
                        public String ign = "";
                        @JsonProperty("satellite_count")
                        public Integer satelliteCount=0;
                        @JsonProperty("Driver_First_Name")
                        public String driverFirstName = "";
                        @JsonProperty("Angle")
                        public String angle = "";
                        @JsonProperty("Ibutton/RFID")
                        public String ibuttonRfid = "";
                        @JsonProperty("SOS")
                        public String sos = "";
                        @JsonProperty("Fuel")
                        public List<String> fuel=new ArrayList<>();
                        @JsonProperty("battery_percentage")
                        public Integer batteryPercentage=0;
                        @JsonProperty("ExternalVolt")
                        public String externalVolt = "";
                        @JsonProperty("Driver_Last_Name")
                        public String driverLastName = "";
                        @JsonProperty("Power")
                        public String power = "";
                        @JsonProperty("username")
                        public String username = "";
                        @JsonProperty("Location")
                        public String location = "";
                        @JsonProperty("Altitude")
                        public String altitude = "";
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
                // Storage storage = Context.getStorage();
                VehicleDataResponse response = new VehicleDataResponse();

                Set<String> imeiSet = new HashSet<>();
                Set<String> vehicleNoSet = new HashSet<>();
                boolean filterByCompany = request.company_names != null && !request.company_names.isEmpty();
                boolean filterByImei = request.imei_nos != null && !request.imei_nos.isEmpty();
                boolean filterByVehicleNo = request.vehicle_nos != null && !request.vehicle_nos.isEmpty();

                if (filterByImei) {
                        for (String imei : request.imei_nos.split(",")) {
                                imeiSet.add(imei.trim());
                        }
                }
                if (filterByVehicleNo) {
                        for (String vno : request.vehicle_nos.split(",")) {
                                vehicleNoSet.add(vno.trim());
                        }
                }
                // Collection<Device> devices = storage.getObjects(Device.class, new
                // Request((Condition) null));
                List<Device> devices = storage.getObjects(Device.class,
                                new Request(new org.traccar.storage.query.Columns.All()));

                for (Device device : devices) {
                        /* revisit */
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
                                if (request.company_names.equalsIgnoreCase(company)) {
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
                                // if (filterByVehicleNo && vehicleNo != null) {
                                // // Case-insensitive check for vehicleNo
                                // for (String vno : vehicleNoSet) {
                                // if (vehicleNo.equalsIgnoreCase(vno)) {
                                // matches = true;
                                // break;
                                // }
                                // }
                                // }
                        } else if (!filterByCompany && !filterByImei && !filterByVehicleNo) {
                                // If no filters are provided, include all devices
                                matches = true;
                        }

                        if (!matches)
                                continue;

                        /* revisit end */
                        Position position = null; // revisit
                        if (device.getPositionId() != 0) {
                                Condition posCond = new Condition.Equals("id", device.getPositionId());
                                Request posReq = new Request(new org.traccar.storage.query.Columns.All(),posCond);
                                position = storage.getObject(Position.class, posReq);
                        }
                        VehicleDataResponse.VehicleData vehicle = new VehicleDataResponse.VehicleData();
                        vehicle.vehicleName = device.getName();
                        vehicle.company = device.getAttributes() != null && device.getAttributes().get("Company") != null
                                                ? device.getAttributes().get("Company").toString()
                                                : "--"; // revisit

                        vehicle.vehicleNo = device.getAttributes() != null && device.getAttributes().get("vehicleNo") != null ? device.getAttributes().get("vehicleNo").toString() : "";

                        vehicle.imeino = device.getUniqueId();

                        vehicle.deviceModel = device.getModel() != null ? device.getModel() : "";

                        vehicle.status = device.getStatus() != null ? device.getStatus() : "";

                        vehicle.username = device.getAttributes() != null && device.getAttributes().get("username") != null
                                                ? device.getAttributes().get("username").toString()
                                                : "";

                        vehicle.datetime = device.getLastUpdate() != null ? new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(device.getLastUpdate()) : "";

                        // Position-based fields
                        if (position != null) {
                                vehicle.latitude = String.valueOf(position.getLatitude());
                                vehicle.longitude = String.valueOf(position.getLongitude());
                                vehicle.speed = position.getSpeed() <= 0.01
                                                ? String.valueOf(Math.round(position.getSpeed() * 1.852))
                                                : "0";
                                vehicle.gpsActualTime = position.getFixTime() != null ? new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(position.getFixTime()) : "";
                                vehicle.angle = String.valueOf((int) position.getCourse());
                                vehicle.altitude = String.valueOf(position.getAltitude());

                                vehicle.satelliteCount = position.getAttributes() != null && position.getAttributes().get(Position.KEY_SATELLITES) instanceof Number
                                                                ? ((Number) position.getAttributes().get(Position.KEY_SATELLITES)).intValue()
                                                                : 0;

                                vehicle.gpsHdop = position.getAttributes() != null && position.getAttributes().get(Position.KEY_HDOP) != null
                                                ? position.getAttributes().get(Position.KEY_HDOP).toString()
                                                : "NA";

                                vehicle.odometer = position.getAttributes() != null && position.getAttributes().get(Position.KEY_ODOMETER) != null
                                                        ? position.getAttributes().get(Position.KEY_ODOMETER).toString()
                                                        : "0";

                                vehicle.location = position.getAddress() != null ? position.getAddress() : "";

                                vehicle.ac = position.getAttributes() != null && position.getAttributes().get("ac") != null
                                                        ? position.getAttributes().get("ac").toString()
                                                        : "--";
                                vehicle.power = position.getAttributes() != null && position.getAttributes().get(Position.KEY_POWER) != null
                                                ? position.getAttributes().get(Position.KEY_POWER).toString()
                                                : "--";
                                vehicle.externalVolt = position.getAttributes() != null && position.getAttributes().get(Position.KEY_BATTERY) != null
                                                        ? position.getAttributes().get(Position.KEY_BATTERY).toString()
                                                        : "--";
                                
                                vehicle.batteryPercentage = position.getAttributes() != null && position.getAttributes().get(Position.KEY_BATTERY_LEVEL) != null
                                                                ? ((Number) position.getAttributes().get(Position.KEY_BATTERY_LEVEL)).intValue()
                                                                : 0;
                                
                                vehicle.temperature = position.getAttributes() != null && position.getAttributes().get(Position.KEY_DEVICE_TEMP) != null
                                                        ? position.getAttributes().get(Position.KEY_DEVICE_TEMP).toString()
                                                        : "--";
                                vehicle.heartbeat = position.getAttributes().get("heartbeat") != null
                                                ? position.getAttributes().get("heartbeat").toString()
                                                : "no";
                                vehicle.door1 = position.getAttributes().get("door1") != null
                                                ? position.getAttributes().get("door1").toString()
                                                : "--";
                                vehicle.door2 = position.getAttributes().get("door2") != null
                                                ? position.getAttributes().get("door2").toString()
                                                : "--";
                                vehicle.door3 = position.getAttributes().get("door3") != null
                                                ? position.getAttributes().get("door3").toString()
                                                : "--";
                                vehicle.door4 = position.getAttributes().get("door4") != null
                                                ? position.getAttributes().get("door4").toString()
                                                : "--";
                                vehicle.elock = position.getAttributes().get("elock") != null
                                                ? position.getAttributes().get("elock").toString()
                                                : "--";
                                vehicle.immobilizeState = device.getAttributes() != null && device.getAttributes().get("immobilizeState") != null
                                                        ? device.getAttributes().get("immobilizeState").toString()
                                                        : "--";
                                vehicle.ign = position.getAttributes() != null && position.getAttributes().get(Position.KEY_IGNITION) != null
                                                ? position.getAttributes().get(Position.KEY_IGNITION).toString()
                                                : "--";

                                vehicle.sos = position.getAttributes() != null
                                                && position.getAttributes().get(Position.KEY_ALARM) != null
                                                && position.getAttributes().get(Position.KEY_ALARM).toString()
                                                                .contains(Position.ALARM_SOS)
                                                                                ? Position.ALARM_SOS
                                                                                : "--";
                                vehicle.poi = position.getAttributes() != null
                                                && position.getAttributes().get("poi") != null
                                                                ? position.getAttributes().get("poi").toString()
                                                                : "--";
                                Object fuelObj = position.getAttributes().get("fuel");
                                if (fuelObj instanceof List<?>) {
                                        @SuppressWarnings("unchecked")
                                        List<String> fuelList = (List<String>) fuelObj;
                                        vehicle.fuel = fuelList;
                                } else if (fuelObj != null) {
                                        vehicle.fuel = new ArrayList<>();
                                        vehicle.fuel.add(fuelObj.toString());
                                } else {
                                        vehicle.fuel = new ArrayList<>();
                                }
                                vehicle.mcc = position.getNetwork() != null
                                                && position.getNetwork().getCellTowers() != null
                                                && !position.getNetwork().getCellTowers().isEmpty()
                                                                ? String.valueOf(position.getNetwork().getCellTowers()
                                                                                .iterator().next().getMobileCountryCode())
                                                                : "--";
                                vehicle.mnc = position.getNetwork() != null
                                                && position.getNetwork().getCellTowers() != null
                                                && !position.getNetwork().getCellTowers().isEmpty()
                                                                ? String.valueOf(position.getNetwork().getCellTowers()
                                                                                .iterator().next().getMobileNetworkCode())
                                                                : "--";
                                vehicle.cellid = position.getNetwork() != null
                                                && position.getNetwork().getCellTowers() != null
                                                && !position.getNetwork().getCellTowers().isEmpty()
                                                                ? String.valueOf(position.getNetwork().getCellTowers()
                                                                                .iterator().next().getCellId())
                                                                : "NA";
                                vehicle.lac = position.getNetwork() != null
                                                && position.getNetwork().getCellTowers() != null
                                                && !position.getNetwork().getCellTowers().isEmpty()
                                                                ? String.valueOf(position.getNetwork().getCellTowers()
                                                                                .iterator().next().getLocationAreaCode())
                                                                : "";
                        }

                        // Other attributes (if available)
                        vehicle.branch = device.getAttributes() != null && device.getAttributes().get("branch") != null
                                        ? device.getAttributes().get("branch").toString()
                                        : "";
                        vehicle.vehicleType = device.getAttributes() != null
                                        && device.getAttributes().get("vehicleType") != null
                                                        ? device.getAttributes().get("vehicleType").toString()
                                                        : "";
                        vehicle.driverFirstName = device.getAttributes() != null
                                        && device.getAttributes().get("driverFirstName") != null
                                                        ? device.getAttributes().get("driverFirstName").toString()
                                                        : "--";
                        vehicle.driverMiddleName = device.getAttributes() != null
                                        && device.getAttributes().get("driverMiddleName") != null
                                                        ? device.getAttributes().get("driverMiddleName").toString()
                                                        : "--";
                        vehicle.driverLastName = device.getAttributes() != null
                                        && device.getAttributes().get("driverLastName") != null
                                                        ? device.getAttributes().get("driverLastName").toString()
                                                        : "--";
                        vehicle.ibuttonRfid = device.getAttributes() != null
                                        && device.getAttributes().get("ibutton") != null
                                                        ? device.getAttributes().get("ibutton").toString()
                                                        : "--";

                        response.root.vehicleData.add(vehicle);
                }

                return Response.ok(response).build();
        }
}