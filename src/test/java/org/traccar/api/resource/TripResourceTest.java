package org.traccar.api.resource;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traccar.api.security.PermissionsService;
import org.traccar.model.Device;
import org.traccar.model.Driver;
import org.traccar.model.Group;
import org.traccar.model.Position;
import org.traccar.model.Trip;
import org.traccar.model.User;
import org.traccar.storage.Storage;
import org.traccar.storage.query.Request;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TripResourceTest {

    private TripResource tripResource;
    private Storage storage;
    private PermissionsService permissionsService;

    @BeforeEach
    public void setUp() {
        storage = mock(Storage.class);
        permissionsService = mock(PermissionsService.class);
        
        tripResource = new TripResource() {
            @Override
            protected long getUserId() {
                return 1L; // Mock user ID
            }
        };
        
        // Use reflection to inject mocked dependencies
        try {
            // Inject storage (from BaseResource)
            Field storageField = findField(tripResource.getClass(), "storage");
            storageField.setAccessible(true);
            storageField.set(tripResource, storage);
            
            // Inject permissionsService (from BaseResource)
            Field permissionsField = findField(tripResource.getClass(), "permissionsService");
            permissionsField.setAccessible(true);
            permissionsField.set(tripResource, permissionsService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks: " + e.getMessage(), e);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new RuntimeException("Field " + fieldName + " not found");
    }

    private Device createMockDevice(long id, String uniqueId, String name, long groupId) {
        Device device = new Device();
        device.setId(id);
        device.setUniqueId(uniqueId);
        device.setName(name);
        device.setGroupId(groupId);
        device.setPositionId(100L);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("assetType", "Truck");
        device.setAttributes(attributes);
        return device;
    }

    private Position createMockPosition(long id, double lat, double lon, double speed) {
        Position position = new Position();
        position.setId(id);
        position.setLatitude(lat);
        position.setLongitude(lon);
        position.setSpeed(speed);
        position.setFixTime(new Date());
        return position;
    }

    private User createMockUser(long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }

    private Group createMockGroup(long id, String name) {
        Group group = new Group();
        group.setId(id);
        group.setName(name);
        return group;
    }

    private Driver createMockDriver(String uniqueId, String name, String phone, String license) {
        Driver driver = new Driver();
        driver.setUniqueId(uniqueId);
        driver.setName(name);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("phone", phone);
        attributes.put("license", license);
        driver.setAttributes(attributes);
        return driver;
    }

    private Trip createValidTrip() {
        Trip trip = new Trip();
        trip.setName("Test Trip");
        trip.setDeviceId(1L);
        trip.setOriginLatitude(28.6139);
        trip.setOriginLongitude(77.2090);
        trip.setDestinationLatitude(19.0760);
        trip.setDestinationLongitude(72.8777);
        trip.setEtaDate(new Date(System.currentTimeMillis() + 86400000)); // Tomorrow
        trip.setConsignorId(10L);
        trip.setConsigneeId(20L);
        return trip;
    }

    @Test
    public void testValidateAndEnrichTrip_Success() throws Exception {
        Trip trip = createValidTrip();
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        User mockConsignor = createMockUser(10L, "Consignor Name");
        User mockConsignee = createMockUser(20L, "Consignee Name");
        Group mockGroup = createMockGroup(5L, "Test Group");
        
        // Set up storage mocks with proper class-based matching
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Group.class), any(Request.class))).thenReturn(mockGroup);
        
        // Mock User queries - Use sequential returns based on call order
        // First call is for consignor (ID 10), second call is for consignee (ID 20)
        when(storage.getObject(eq(User.class), any(Request.class)))
            .thenReturn(mockConsignor)  // First call - consignor
            .thenReturn(mockConsignee); // Second call - consignee
        
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        // Access private method via reflection
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip, false);
        
        // Assertions
        assertEquals("DEV001", trip.getDeviceUniqueId());
        assertEquals("Test Device", trip.getDeviceName());
        assertEquals("DEV001", trip.getAssetNumber());
        assertEquals("Truck", trip.getAssetType());
        assertEquals("Test Group", trip.getTransporterName());
        assertEquals("Consignor Name", trip.getConsignorName());
        assertEquals("Consignee Name", trip.getConsigneeName());
        assertNotNull(trip.getDepartureDate());
        assertNotNull(trip.getCreatedAt());
        assertNotNull(trip.getUpdatedAt());
    }

    @Test
    public void testValidateAndEnrichTrip_MissingDeviceId() throws Exception {
        Trip trip = createValidTrip();
        trip.setDeviceId(0); // Invalid device ID
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        
        var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, 
            () -> method.invoke(tripResource, trip, false));
        
        assertTrue(exception.getCause() instanceof WebApplicationException);
        WebApplicationException wae = (WebApplicationException) exception.getCause();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), wae.getResponse().getStatus());
    }

    @Test
    public void testValidateAndEnrichTrip_MissingOriginCoordinates() throws Exception {
        Trip trip = createValidTrip();
        trip.setOriginLatitude(0);
        trip.setOriginLongitude(0);
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        
        var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, 
            () -> method.invoke(tripResource, trip, false));
        
        assertTrue(exception.getCause() instanceof WebApplicationException);
        WebApplicationException wae = (WebApplicationException) exception.getCause();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), wae.getResponse().getStatus());
    }

    @Test
    public void testValidateAndEnrichTrip_MissingDestinationCoordinates() throws Exception {
        Trip trip = createValidTrip();
        trip.setDestinationLatitude(0);
        trip.setDestinationLongitude(0);
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        
        var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, 
            () -> method.invoke(tripResource, trip, false));
        
        assertTrue(exception.getCause() instanceof WebApplicationException);
        WebApplicationException wae = (WebApplicationException) exception.getCause();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), wae.getResponse().getStatus());
    }

    @Test
    public void testValidateAndEnrichTrip_MissingEtaDate() throws Exception {
        Trip trip = createValidTrip();
        trip.setEtaDate(null);
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        
        var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, 
            () -> method.invoke(tripResource, trip, false));
        
        assertTrue(exception.getCause() instanceof WebApplicationException);
        WebApplicationException wae = (WebApplicationException) exception.getCause();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), wae.getResponse().getStatus());
    }

    @Test
    public void testValidateAndEnrichTrip_DeviceNotFound() throws Exception {
        Trip trip = createValidTrip();
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(null);
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        
        var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, 
            () -> method.invoke(tripResource, trip, false));
        
        assertTrue(exception.getCause() instanceof WebApplicationException);
        WebApplicationException wae = (WebApplicationException) exception.getCause();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), wae.getResponse().getStatus());
    }

    @Test
    public void testValidateAndEnrichTrip_WithDeviceUniqueId() throws Exception {
        Trip trip = createValidTrip();
        trip.setDeviceId(0);
        trip.setDeviceUniqueId("DEV001");
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        User mockConsignor = createMockUser(10L, "Consignor Name");
        User mockConsignee = createMockUser(20L, "Consignee Name");
        Group mockGroup = createMockGroup(5L, "Test Group");
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Group.class), any(Request.class))).thenReturn(mockGroup);
        when(storage.getObject(eq(User.class), any(Request.class)))
            .thenReturn(mockConsignor)
            .thenReturn(mockConsignee);
        
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip, false);
        
        assertEquals(1L, trip.getDeviceId());
        assertEquals("DEV001", trip.getDeviceUniqueId());
    }

    @Test
    public void testValidateAndEnrichTrip_WithDriverDetails() throws Exception {
        Trip trip = createValidTrip();
        trip.setDriverNumber("DRV001");
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        User mockConsignor = createMockUser(10L, "Consignor Name");
        User mockConsignee = createMockUser(20L, "Consignee Name");
        Group mockGroup = createMockGroup(5L, "Test Group");
        Driver mockDriver = createMockDriver("DRV001", "John Doe", "+1234567890", "DL12345");
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Group.class), any(Request.class))).thenReturn(mockGroup);
        when(storage.getObject(eq(User.class), any(Request.class)))
            .thenReturn(mockConsignor)
            .thenReturn(mockConsignee);
        when(storage.getObject(eq(Driver.class), any(Request.class))).thenReturn(mockDriver);
        
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip, false);
        
        assertEquals("John Doe", trip.getDriverName());
        assertEquals("+1234567890", trip.getDriverContact());
        assertEquals("DL12345", trip.getDriverLicense());
    }

    @Test
    public void testEnrichTripWithPositionData_Success() throws Exception {
        Trip trip = createValidTrip();
        trip.setDeviceId(1L);
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        Position mockPosition = createMockPosition(100L, 28.7041, 77.1025, 50.0);
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Position.class), any(Request.class))).thenReturn(mockPosition);
        
        var method = TripResource.class.getDeclaredMethod("enrichTripWithPositionData", Trip.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip);
        
        assertEquals(28.7041, trip.getCurrentLatitude(), 0.001);
        assertEquals(77.1025, trip.getCurrentLongitude(), 0.001);
        assertEquals(50.0, trip.getCurrentSpeed(), 0.001);
        assertNotNull(trip.getLastPositionUpdate());
    }

    @Test
    public void testEnrichTripWithPositionData_NoPosition() throws Exception {
        Trip trip = createValidTrip();
        trip.setDeviceId(1L);
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        mockDevice.setPositionId(0); // No position
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        
        var method = TripResource.class.getDeclaredMethod("enrichTripWithPositionData", Trip.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip);
        
        // Should initialize to default values
        assertEquals(0.0, trip.getCurrentLatitude());
        assertEquals(0.0, trip.getCurrentLongitude());
        assertEquals(0.0, trip.getCurrentSpeed());
        assertNull(trip.getLastPositionUpdate());
        assertEquals(0.0, trip.getDistanceCovered());
    }

    @Test
    public void testEnrichTripWithPositionData_DeviceNotFound() throws Exception {
        Trip trip = createValidTrip();
        trip.setDeviceId(1L);
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(null);
        
        var method = TripResource.class.getDeclaredMethod("enrichTripWithPositionData", Trip.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip);
        
        // Should initialize to default values without throwing exception
        assertEquals(0.0, trip.getCurrentLatitude());
        assertEquals(0.0, trip.getCurrentLongitude());
    }

    @Test
    public void testCalculateDistance_ValidCoordinates() throws Exception {
        var method = TripResource.class.getDeclaredMethod(
            "calculateDistance", double.class, double.class, double.class, double.class);
        method.setAccessible(true);
        
        // Test Delhi to Mumbai (~1150 km straight-line distance)
        double distance = (double) method.invoke(tripResource, 28.6139, 77.2090, 19.0760, 72.8777);
        
        // Haversine should return ~1150 km (actual driving is ~1400 km)
        assertTrue(distance > 1100 && distance < 1200, 
            "Distance should be approximately 1150 km, got: " + distance);
    }

    @Test
    public void testCalculateDistance_SameLocation() throws Exception {
        var method = TripResource.class.getDeclaredMethod(
            "calculateDistance", double.class, double.class, double.class, double.class);
        method.setAccessible(true);
        
        double distance = (double) method.invoke(tripResource, 28.6139, 77.2090, 28.6139, 77.2090);
        
        assertEquals(0.0, distance, 0.001, "Distance between same coordinates should be 0");
    }

    @Test
    public void testValidateAndEnrichTrip_ConsignorNotFound_WithOtherName() throws Exception {
        Trip trip = createValidTrip();
        trip.setOtherConsignorName("Custom Consignor");
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        User mockConsignee = createMockUser(20L, "Consignee Name");
        Group mockGroup = createMockGroup(5L, "Test Group");
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Group.class), any(Request.class))).thenReturn(mockGroup);
        when(storage.getObject(eq(User.class), any(Request.class)))
            .thenReturn(null)
            .thenReturn(mockConsignee);
        
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip, false);
        
        assertEquals("Custom Consignor", trip.getConsignorName());
        assertEquals("Custom Consignor", trip.getOtherConsignorName());
    }

    @Test
    public void testValidateAndEnrichTrip_ConsigneeNotFound_WithOtherName() throws Exception {
        Trip trip = createValidTrip();
        trip.setOtherConsigneeName("Custom Consignee");
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        User mockConsignor = createMockUser(10L, "Consignor Name");
        Group mockGroup = createMockGroup(5L, "Test Group");
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Group.class), any(Request.class))).thenReturn(mockGroup);
        when(storage.getObject(eq(User.class), any(Request.class)))
            .thenReturn(mockConsignor)
            .thenReturn(null);
        
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip, false);
        
        assertEquals("Custom Consignee", trip.getConsigneeName());
        assertEquals("Custom Consignee", trip.getOtherConsigneeName());
    }

    @Test
    public void testValidateAndEnrichTrip_ConsignorNotFound_NoOtherName() throws Exception {
        Trip trip = createValidTrip();
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        Group mockGroup = createMockGroup(5L, "Test Group");
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(User.class), argThat(req -> 
            req.getCondition().toString().contains("10")))).thenReturn(null); // Consignor not found
        when(storage.getObject(eq(Group.class), any(Request.class))).thenReturn(mockGroup);
        
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        
        var exception = assertThrows(java.lang.reflect.InvocationTargetException.class, 
            () -> method.invoke(tripResource, trip, false));
        
        assertTrue(exception.getCause() instanceof WebApplicationException);
        WebApplicationException wae = (WebApplicationException) exception.getCause();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), wae.getResponse().getStatus());
        assertTrue(wae.getResponse().getEntity().toString().contains("Consignor"));
    }

    @Test
    public void testValidateAndEnrichTrip_Update_RouteChanged() throws Exception {
        Trip trip = createValidTrip();
        trip.setId(1L);
        
        // Create existing trip with different destination
        Trip existingTrip = createValidTrip();
        existingTrip.setDestinationLatitude(20.0);
        existingTrip.setDestinationLongitude(73.0);
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        User mockConsignor = createMockUser(10L, "Consignor Name");
        User mockConsignee = createMockUser(20L, "Consignee Name");
        Group mockGroup = createMockGroup(5L, "Test Group");
        
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(existingTrip);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Group.class), any(Request.class))).thenReturn(mockGroup);
        when(storage.getObject(eq(User.class), any(Request.class)))
            .thenReturn(mockConsignor)
            .thenReturn(mockConsignee);
        
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip, true); // isUpdate = true
        
        assertNotNull(trip.getUpdatedAt());
        assertNull(trip.getCreatedAt()); // Should not be set on update
    }

    @Test
    public void testValidateAndEnrichTrip_SetsTripStatusForNewTrips() throws Exception {
        Trip trip = createValidTrip();
        trip.setDepartureDate(new Date()); // Current time = should be "running"
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        User mockConsignor = createMockUser(10L, "Consignor Name");
        User mockConsignee = createMockUser(20L, "Consignee Name");
        Group mockGroup = createMockGroup(5L, "Test Group");
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Group.class), any(Request.class))).thenReturn(mockGroup);
        when(storage.getObject(eq(User.class), any(Request.class)))
            .thenReturn(mockConsignor)
            .thenReturn(mockConsignee);
        
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip, false); // isUpdate = false (new trip)
        
        assertEquals("running", trip.getTripStatus());
    }

    @Test
    public void testValidateAndEnrichTrip_SetsPendingStatusForFutureTrips() throws Exception {
        Trip trip = createValidTrip();
        // Set departure date to 2 hours in the future
        trip.setDepartureDate(new Date(System.currentTimeMillis() + 2 * 60 * 60 * 1000));
        
        Device mockDevice = createMockDevice(1L, "DEV001", "Test Device", 5L);
        User mockConsignor = createMockUser(10L, "Consignor Name");
        User mockConsignee = createMockUser(20L, "Consignee Name");
        Group mockGroup = createMockGroup(5L, "Test Group");
        
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Group.class), any(Request.class))).thenReturn(mockGroup);
        when(storage.getObject(eq(User.class), any(Request.class)))
            .thenReturn(mockConsignor)
            .thenReturn(mockConsignee);
        
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
        
        var method = TripResource.class.getDeclaredMethod("validateAndEnrichTrip", Trip.class, boolean.class);
        method.setAccessible(true);
        method.invoke(tripResource, trip, false); // isUpdate = false (new trip)
        
        assertEquals("pending", trip.getTripStatus());
    }
}

