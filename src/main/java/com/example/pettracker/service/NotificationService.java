package com.example.pettracker.service;


import com.example.pettracker.entity.User;
import com.example.pettracker.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class NotificationService {

    @Value("${notifications.email.from:}")
    private String emailFrom;

    private final JavaMailSender mailSender;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final SmsProvider smsProvider;

    public NotificationService(ObjectProvider<JavaMailSender> mailSenderProvider,
                               SimpMessagingTemplate messagingTemplate,
                               UserRepository userRepository,
                               SmsProvider smsProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
        this.smsProvider = smsProvider;
    }

    public void sendSms(String to, String body) {
        log.info("Sending SMS notification to {} - Body: {}", to, body);
        smsProvider.sendSms(to, body);
    }

    public void sendWhatsApp(String to, String body) {
        smsProvider.sendWhatsApp(to, body);
    }

    public void sendPhoneNotification(String to, String body) {
        log.info("Sending phone notification to {} - Body: {}", to, body);

        if (!hasText(to) || !hasText(body)) {
            return;
        }
        log.info("Proceeding with phone notification to {}", to);

        sendSms(to, body);
        if (smsProvider.isWhatsAppConfigured()) {
            sendWhatsApp(to, body);
        }
    }

    public void sendEmail(String to, String subject, String body) {
        try {
            if (!hasText(to) || !hasText(subject) || !hasText(body)) {
                log.warn("Email skipped: destination, subject, or body missing");
                return;
            }
            if (mailSender == null) {
                log.warn("Email skipped for {}: Spring Mail is not configured", to);
                return;
            }
            SimpleMailMessage m = new SimpleMailMessage();
            m.setTo(to);
            m.setSubject(subject);
            m.setText(body);
            if (hasText(emailFrom)) {
                m.setFrom(emailFrom);
            }
            mailSender.send(m);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }
    @Async("notificationExecutor")
    @Transactional(readOnly = true)
    public void notifyOwner(User owner, String message) {
        log.info("Sending owner notification - Body: {}", message);
        if (owner == null) return;
        Long ownerId = owner.getId();
        if (ownerId == null) return;

        User managedOwner = userRepository.findById(ownerId).orElse(null);
        if (managedOwner == null) return;
        log.info("Retrieved managed owner with ID: {}", ownerId);

        notifyOwnerContacts(managedOwner, "Pet Alert", message, message, message);
    }
    @Transactional(readOnly = true)
    public void notifyLostPet(User owner, String petName, String petType, String additionalInfo) {
        if (owner == null) return;
        Long ownerId = owner.getId();
        if (ownerId == null) return;

        User managedOwner = userRepository.findById(ownerId).orElse(null);
        if (managedOwner == null) return;

        // Create a comprehensive message for lost pet
        String message = buildLostPetMessage(petName, petType, additionalInfo);

        String smsBody = "ALERT: Your " + petType + " '" + petName + "' has been marked as LOST. " +
                "Please take action immediately. Check your email for details.";
        log.info("Sending lost pet SMS notification - Body: {}", smsBody);

        notifyOwnerContacts(
                managedOwner,
                "URGENT: Your Pet '" + petName + "' is Lost",
                smsBody,
                message,
                "Your pet " + petName + " (" + petType + ") has been marked as lost!"
        );
    }

    private void notifyOwnerContacts(User owner, String emailSubject, String smsBody, String emailBody, String websocketBody) {
        log.info("Notifying owner {} - Email subject: {}, SMS body: {}", owner.getId(), emailSubject, smsBody);

        if (owner.getPhone() != null && !owner.getPhone().isBlank()) {
            sendPhoneNotification(owner.getPhone(), smsBody);
        }
        if (owner.getEmail() != null && !owner.getEmail().isBlank()) {
            sendEmail(owner.getEmail(), emailSubject, emailBody);
        }
        sendRealtimeAlert(owner, websocketBody);
    }

    public void sendRealtimeAlert(User owner, String message) {
        if (owner == null || owner.getId() == null || !hasText(message)) {
            return;
        }
        try {
            messagingTemplate.convertAndSend("/topic/alerts/" + owner.getId(), message);
        } catch (Exception e) {
            log.warn("Failed to publish WebSocket alert for owner {}: {}", owner.getId(), e.getMessage(), e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String buildLostPetMessage(String petName, String petType, String additionalInfo) {
        StringBuilder message = new StringBuilder();
        message.append("⚠️ URGENT PET ALERT ⚠️\n\n");
        message.append("Your ").append(petType).append(" '").append(petName).append("' has been marked as LOST.\n\n");
        message.append("IMMEDIATE ACTIONS:\n");
        message.append("1. Check your pet tracker app for last known location\n");
        message.append("2. Contact local animal shelters and vets\n");
        message.append("3. Post on local lost pet community pages\n");
        message.append("4. Alert neighbors and friends\n\n");

        if (additionalInfo != null && !additionalInfo.isBlank()) {
            message.append("ADDITIONAL DETAILS:\n").append(additionalInfo).append("\n\n");
        }

        message.append("Share your pet's information with volunteers to help with the search.\n");
        message.append("Stay in touch via the app for real-time updates.\n\n");
        message.append("We're here to help find your pet!");

        return message.toString();
    }

}
