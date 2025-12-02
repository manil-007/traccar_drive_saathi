package org.traccar.api.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traccar.api.security.PermissionsService;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.model.Trip;
import org.traccar.storage.Storage;
import org.traccar.storage.query.Request;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Trip completion functionality.
 * Tests the POST /trips/{id}/complete endpoint.
 */
class TripCompletionTest {

    private Storage storage;
    private PermissionsService permissionsService;
    private TripResource tripResource;

    private Trip mockTrip;
    private Device mockDevice;
    private Position mockPosition;

    @BeforeEach
    void setUp() throws Exception {
        storage = mock(Storage.class);
        permissionsService = mock(PermissionsService.class);
        
        tripResource = new TripResource() {
            @Override
            protected long getUserId() {
                return 1L; // Mock user ID
            }
        };
        
        // Use reflection to inject mocked dependencies
        injectField(tripResource, "storage", storage);
        injectField(tripResource, "permissionsService", permissionsService);

        // Create a mock trip in "running" status
        mockTrip = new Trip();
        mockTrip.setId(1L);
        mockTrip.setName("Test Trip");
        mockTrip.setDeviceId(100L);
        mockTrip.setTripStatus("running");
        mockTrip.setOriginLatitude(28.6139);
        mockTrip.setOriginLongitude(77.2090);
        mockTrip.setDestinationLatitude(19.0760);
        mockTrip.setDestinationLongitude(72.8777);
        
        // Set departure date to 2 hours ago
        Date departureDate = new Date(System.currentTimeMillis() - (2 * 60 * 60 * 1000));
        mockTrip.setDepartureDate(departureDate);

        // Create mock device with position
        mockDevice = new Device();
        mockDevice.setId(100L);
        mockDevice.setPositionId(500L);

        // Create mock position
        mockPosition = new Position();
        mockPosition.setId(500L);
        mockPosition.setLatitude(19.0760);
        mockPosition.setLongitude(72.8777);
        mockPosition.setSpeed(0.0);
        mockPosition.setFixTime(new Date());

        // Mock permission check
        doNothing().when(permissionsService).checkPermission(any(), anyLong(), anyLong());
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = null;
        Class<?> clazz = target.getClass().getSuperclass();
        while (clazz != null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (field == null) {
            field = target.getClass().getDeclaredField(fieldName);
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testCompleteTrip_Success() throws Exception {
        // Arrange
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Position.class), any(Request.class))).thenReturn(mockPosition);
        doNothing().when(storage).updateObject(any(Trip.class), any(Request.class));

        // Act
        Response response = tripResource.completeTrip(1L, "Delivered successfully");

        // Assert
        assertEquals(200, response.getStatus());
        Trip completedTrip = (Trip) response.getEntity();
        
        assertNotNull(completedTrip);
        assertEquals("completed", completedTrip.getTripStatus());
        assertNotNull(completedTrip.getCompletionDate());
        assertEquals(19.0760, completedTrip.getCompletionLatitude(), 0.0001);
        assertEquals(72.8777, completedTrip.getCompletionLongitude(), 0.0001);
        assertEquals("Delivered successfully", completedTrip.getCompletionNotes());
        assertNotNull(completedTrip.getTotalTripTimeMinutes());
        assertTrue(completedTrip.getTotalTripTimeMinutes() >= 119 && 
                   completedTrip.getTotalTripTimeMinutes() <= 121); // ~120 minutes

        // Verify storage update was called
        verify(storage).updateObject(any(Trip.class), any(Request.class));
    }

    @Test
    void testCompleteTrip_WithoutNotes() throws Exception {
        // Arrange
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Position.class), any(Request.class))).thenReturn(mockPosition);
        doNothing().when(storage).updateObject(any(Trip.class), any(Request.class));

        // Act
        Response response = tripResource.completeTrip(1L, null);

        // Assert
        assertEquals(200, response.getStatus());
        Trip completedTrip = (Trip) response.getEntity();
        assertEquals("completed", completedTrip.getTripStatus());
        assertNull(completedTrip.getCompletionNotes());
    }

    @Test
    void testCompleteTrip_TripNotFound() throws Exception {
        // Arrange
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(null);

        // Act & Assert
        WebApplicationException exception = assertThrows(
            WebApplicationException.class,
            () -> tripResource.completeTrip(999L, "Notes")
        );
        
        assertEquals(404, exception.getResponse().getStatus());
        assertEquals("Trip not found", exception.getResponse().getEntity());
    }

    @Test
    void testCompleteTrip_AlreadyCompleted() throws Exception {
        // Arrange
        mockTrip.setTripStatus("completed");
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);

        // Act & Assert
        WebApplicationException exception = assertThrows(
            WebApplicationException.class,
            () -> tripResource.completeTrip(1L, "Notes")
        );
        
        assertEquals(400, exception.getResponse().getStatus());
        assertTrue(exception.getResponse().getEntity().toString().contains("already completed"));
    }

    @Test
    void testCompleteTrip_CancelledTrip() throws Exception {
        // Arrange
        mockTrip.setTripStatus("cancelled");
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);

        // Act & Assert
        WebApplicationException exception = assertThrows(
            WebApplicationException.class,
            () -> tripResource.completeTrip(1L, "Notes")
        );
        
        assertEquals(400, exception.getResponse().getStatus());
        assertTrue(exception.getResponse().getEntity().toString().contains("cancelled trip"));
    }

    @Test
    void testCompleteTrip_NoPermission() throws Exception {
        // Arrange
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);
        doThrow(new SecurityException("No permission"))
            .when(permissionsService).checkPermission(eq(Device.class), anyLong(), eq(100L));

        // Act & Assert
        assertThrows(SecurityException.class, () -> tripResource.completeTrip(1L, "Notes"));
    }

    @Test
    void testCompleteTrip_NoDevicePosition() throws Exception {
        // Arrange - Device has no position
        mockDevice.setPositionId(0L);
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        doNothing().when(storage).updateObject(any(Trip.class), any(Request.class));

        // Act
        Response response = tripResource.completeTrip(1L, "No position available");

        // Assert
        assertEquals(200, response.getStatus());
        Trip completedTrip = (Trip) response.getEntity();
        assertEquals("completed", completedTrip.getTripStatus());
        assertNull(completedTrip.getCompletionLatitude());
        assertNull(completedTrip.getCompletionLongitude());
    }

    @Test
    void testCompleteTrip_PositionFetchFails() throws Exception {
        // Arrange - Position exists but fetch fails
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Position.class), any(Request.class))).thenReturn(null);
        doNothing().when(storage).updateObject(any(Trip.class), any(Request.class));

        // Act
        Response response = tripResource.completeTrip(1L, "Position fetch failed");

        // Assert - Should still complete successfully
        assertEquals(200, response.getStatus());
        Trip completedTrip = (Trip) response.getEntity();
        assertEquals("completed", completedTrip.getTripStatus());
        assertNull(completedTrip.getCompletionLatitude());
        assertNull(completedTrip.getCompletionLongitude());
    }

    @Test
    void testCompleteTrip_CalculatesTripDuration() throws Exception {
        // Arrange - Trip departed 3 hours and 30 minutes ago
        Date departureDate = new Date(System.currentTimeMillis() - (3 * 60 + 30) * 60 * 1000);
        mockTrip.setDepartureDate(departureDate);
        
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Position.class), any(Request.class))).thenReturn(mockPosition);
        doNothing().when(storage).updateObject(any(Trip.class), any(Request.class));

        // Act
        Response response = tripResource.completeTrip(1L, null);

        // Assert
        Trip completedTrip = (Trip) response.getEntity();
        assertNotNull(completedTrip.getTotalTripTimeMinutes());
        // Should be approximately 210 minutes (3.5 hours), allow Â±1 minute for execution time
        assertTrue(completedTrip.getTotalTripTimeMinutes() >= 209 && 
                   completedTrip.getTotalTripTimeMinutes() <= 211);
    }

    @Test
    void testCompleteTrip_NoDepartureDate() throws Exception {
        // Arrange - Trip has no departure date
        mockTrip.setDepartureDate(null);
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Position.class), any(Request.class))).thenReturn(mockPosition);
        doNothing().when(storage).updateObject(any(Trip.class), any(Request.class));

        // Act
        Response response = tripResource.completeTrip(1L, null);

        // Assert
        Trip completedTrip = (Trip) response.getEntity();
        assertNull(completedTrip.getTotalTripTimeMinutes());
    }

    @Test
    void testCompleteTrip_LongNotesSanitized() throws Exception {
        // Arrange
        String longNotes = "A".repeat(1500); // 1500 characters (exceeds 1000 limit)
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Position.class), any(Request.class))).thenReturn(mockPosition);
        doNothing().when(storage).updateObject(any(Trip.class), any(Request.class));

        // Act
        Response response = tripResource.completeTrip(1L, longNotes);

        // Assert
        Trip completedTrip = (Trip) response.getEntity();
        assertNotNull(completedTrip.getCompletionNotes());
        assertTrue(completedTrip.getCompletionNotes().length() <= 1000);
    }

    @Test
    void testCompleteTrip_UpdatesUpdatedAtTimestamp() throws Exception {
        // Arrange
        Date oldUpdatedAt = new Date(System.currentTimeMillis() - 10000);
        mockTrip.setUpdatedAt(oldUpdatedAt);
        
        when(storage.getObject(eq(Trip.class), any(Request.class))).thenReturn(mockTrip);
        when(storage.getObject(eq(Device.class), any(Request.class))).thenReturn(mockDevice);
        when(storage.getObject(eq(Position.class), any(Request.class))).thenReturn(mockPosition);
        doNothing().when(storage).updateObject(any(Trip.class), any(Request.class));

        // Act
        Response response = tripResource.completeTrip(1L, null);

        // Assert
        Trip completedTrip = (Trip) response.getEntity();
        assertNotNull(completedTrip.getUpdatedAt());
        assertTrue(completedTrip.getUpdatedAt().after(oldUpdatedAt));
    }
}
