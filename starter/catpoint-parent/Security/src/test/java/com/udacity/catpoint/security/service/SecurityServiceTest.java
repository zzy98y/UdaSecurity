package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;

import com.udacity.catpoint.security.application.StatusListener;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private SecurityService securityService;

    private Sensor sensor;


    private final String randomString = UUID.randomUUID().toString();


    private StatusListener statusListener;

    @Mock
    private ImageService imageService;

    @Mock
    private SecurityRepository securityRepository;



    private Sensor getSensor() {
        return new Sensor(randomString, SensorType.DOOR);
    }

    private Set<Sensor> getAllSensors( boolean status, int count) {
        Set<Sensor> sensors = new HashSet<>();
         for (int i = 0; i < count; i++) {
             sensors.add(new Sensor(randomString, SensorType.DOOR));
         }
         sensors.forEach(sensor -> sensor.setActive(status));

         return sensors;
    }


    @BeforeEach
    void setup() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = getSensor();
    }

    @Test
    void TestStatusListener() {
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);
    }

    @Test
    void TestAdd_RemoveSensor() {
        Sensor sensor = new Sensor("test", SensorType.DOOR);
        securityService.addSensor(sensor);
        assertNotNull(securityService.getSensors());
        securityService.removeSensor(sensor);
    }


    // 1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
    @Test
    void ifSystemArmedAndSensorActivated_changeStatusToPending() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    // 2. If alarm is armed and a sensor becomes activated and the system is already pending alarm,
    // set the alarm status to alarm on.
    @Test
    void setAlarmStatusToAlarm_IfAlarmPendingAlarmAndSensorActivated() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository,times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 3. If pending alarm and all sensors are inactive, return to no alarm state.
    @Test
    void noAlarmState_IfNoSensorActivatedAndAlarmPendingAlarm() {
        securityService.changeSensorActivationStatus(sensor);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor,false);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 4. If alarm is active, change in sensor state should not affect the alarm state.
    @ParameterizedTest
    @ValueSource(booleans = {true,false})
    void noAlarmChange_AlarmActiveAndChangeSensor(boolean status) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor,status);

        verify(securityRepository,times(0)).setAlarmStatus(any(AlarmStatus.class));
    }

    // 5. If a sensor is activated while already active and the system is in pending state,
    // change it to alarm state.
    @Test
    void changeAlarmState_IfSensorActivatedAndSystemPendingState() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor,true);

        verify(securityRepository,times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 6. If a sensor is deactivated while already inactive, make no changes to the alarm state.
    @Test
    void noChangeAlarmState_IfSensorDeactivatedWhileInactive() {
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor,false);

        verify(securityRepository,times(0)).setAlarmStatus(any(AlarmStatus.class));
    }

    // 7. If the image service identifies an image containing a cat while the system is armed-home,
    // put the system into alarm status.
    @Test
    void systemAlarm_IfCatImageIdentifiedWhileSystemArmedHome() {
        BufferedImage catImage = new BufferedImage(300,300,BufferedImage.TYPE_INT_RGB);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(catImage, 50.0f)).thenReturn(true);
        securityService.processImage(catImage);

        verify(securityRepository,times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    // 8. If the image service identifies an image that does not contain a cat,
    // change the status to no alarm as long as the sensors are not active.
    @Test
    void changeToNoAlarm_IfNoCatImageIdentifiedAndSensorsNotActive() {
        Set<Sensor> sensors = getAllSensors(false, 4);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(imageService.imageContainsCat(any(),ArgumentMatchers.anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));

        verify(securityRepository,times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // 9. If the system is disarmed, set the status to no alarm.
    @Test
    void setNoAlarm_IfSystemDisarmed() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        verify(securityRepository,times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);

    }

    // 10.If the system is armed, reset all sensors to inactive.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class,names = {"ARMED_HOME","ARMED_AWAY"})
    void resetAllSensorsInactive_IfSystemArmed(ArmingStatus status) {
        Set<Sensor> sensors = getAllSensors(true,4);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityService.getSensors()).thenReturn(sensors);
        securityService.setArmingStatus(status);

        securityService.getSensors().forEach(sensor -> {
            assertFalse(sensor.getActive());
        });

    }

    // 11. If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    @Test
    void setStatusAlarm_IfArmedHomeWhileCameraShowCat() {
        BufferedImage catImage = new BufferedImage(300,300,BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(catImage,50.0f)).thenReturn(true);
        when(securityService.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.processImage(catImage);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(securityRepository,times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }



}