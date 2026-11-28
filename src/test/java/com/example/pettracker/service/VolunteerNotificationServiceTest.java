package com.example.pettracker.service;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.SearchGroup;
import com.example.pettracker.entity.User;
import com.example.pettracker.entity.Volunteer;
import com.example.pettracker.repository.VolunteerRejoinRequestRepository;
import com.example.pettracker.repository.VolunteerRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class VolunteerNotificationServiceTest {

    @ParameterizedTest
    @CsvSource({
            "LOST, Lost pet update: Milo, has been marked as lost.",
            "OUT_OF_ZONE, Geofence alert: Milo, triggered a geofence alert."
    })
    void eachPetAlertNotifiesAUserOnceAcrossMultipleGroups(String alertType, String subject, String detail) {
        NotificationService notifications = mock(NotificationService.class);
        VolunteerRepository memberships = mock(VolunteerRepository.class);
        VolunteerNotificationService service = new VolunteerNotificationService(notifications, memberships,
                mock(VolunteerRejoinRequestRepository.class));
        Pet pet = Pet.builder().id(3L).name("Milo").build();
        User alex = User.builder().id(10L).email("alex@example.com").phone("+15551234567").build();
        User steph = User.builder().id(11L).email("steph@example.com").build();
        SearchGroup north = SearchGroup.builder().id(1L).groupName("North").build();
        SearchGroup south = SearchGroup.builder().id(2L).groupName("South").build();
        when(memberships.findActiveMembersOfActiveGroupsByPetId(3L)).thenReturn(List.of(
                Volunteer.builder().user(alex).searchGroup(south).build(),
                Volunteer.builder().user(steph).searchGroup(north).build(),
                Volunteer.builder().user(alex).searchGroup(north).build()));

        service.notifyPetGroupMembers(pet, alertType);

        String eventMessage = ": Milo " + detail + " Please open FiruApp to coordinate with your group.";
        verify(notifications).sendEmail("alex@example.com", subject, "Groups 'North', 'South'" + eventMessage);
        verify(notifications).sendPhoneNotification("+15551234567", "Groups 'North', 'South'" + eventMessage);
        verify(notifications).sendEmail("steph@example.com", subject, "Group 'North'" + eventMessage);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void contextRegistersTransactionalNotificationListeners() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(ListenerConfiguration.class, VolunteerNotificationService.class);
            context.registerBean(NotificationService.class, () -> mock(NotificationService.class));
            context.registerBean(VolunteerRepository.class, () -> mock(VolunteerRepository.class));
            context.registerBean(VolunteerRejoinRequestRepository.class,
                    () -> mock(VolunteerRejoinRequestRepository.class));

            context.refresh();

            assertNotNull(context.getBean(VolunteerNotificationService.class));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement(proxyTargetClass = true)
    static class ListenerConfiguration {
    }
}
