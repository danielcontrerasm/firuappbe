package com.example.pettracker.service;

import com.example.pettracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        notificationService = spy(new NotificationService(
                mailSenderProvider,
                mock(SimpMessagingTemplate.class),
                mock(UserRepository.class)
        ));

        doNothing().when(notificationService).sendSms(anyString(), anyString());
        doNothing().when(notificationService).sendWhatsApp(anyString(), anyString());
    }

    @Test
    void sendPhoneNotificationSendsSmsAndWhatsAppWhenEnabled() {
        ReflectionTestUtils.setField(notificationService, "twilioSid", "sid");
        ReflectionTestUtils.setField(notificationService, "twilioToken", "token");
        ReflectionTestUtils.setField(notificationService, "twilioWhatsappEnabled", true);
        ReflectionTestUtils.setField(notificationService, "twilioWhatsappNumber", "+14155238886");

        notificationService.sendPhoneNotification("+15551234567", "Pet alert");

        verify(notificationService).sendSms("+15551234567", "Pet alert");
        verify(notificationService).sendWhatsApp("+15551234567", "Pet alert");
    }

    @Test
    void sendPhoneNotificationSkipsWhatsAppWhenDisabled() {
        ReflectionTestUtils.setField(notificationService, "twilioWhatsappEnabled", false);
        ReflectionTestUtils.setField(notificationService, "twilioWhatsappNumber", "+14155238886");

        notificationService.sendPhoneNotification("+15551234567", "Pet alert");

        verify(notificationService).sendSms("+15551234567", "Pet alert");
        verify(notificationService, never()).sendWhatsApp(anyString(), anyString());
    }
}
