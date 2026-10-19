package com.example.pettracker.service;

import com.example.pettracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationService notificationService;
    private SmsProvider smsProvider;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        smsProvider = mock(SmsProvider.class);

        notificationService = new NotificationService(
                mailSenderProvider,
                mock(SimpMessagingTemplate.class),
                mock(UserRepository.class),
                smsProvider
        );
    }

    @Test
    void sendPhoneNotificationSendsSmsAndWhatsAppWhenEnabled() {
        when(smsProvider.isWhatsAppConfigured()).thenReturn(true);

        notificationService.sendPhoneNotification("+15551234567", "Pet alert");

        verify(smsProvider).sendSms("+15551234567", "Pet alert");
        verify(smsProvider).sendWhatsApp("+15551234567", "Pet alert");
    }

    @Test
    void sendPhoneNotificationSkipsWhatsAppWhenDisabled() {
        when(smsProvider.isWhatsAppConfigured()).thenReturn(false);

        notificationService.sendPhoneNotification("+15551234567", "Pet alert");

        verify(smsProvider).sendSms("+15551234567", "Pet alert");
        verify(smsProvider, never()).sendWhatsApp("+15551234567", "Pet alert");
    }
}
