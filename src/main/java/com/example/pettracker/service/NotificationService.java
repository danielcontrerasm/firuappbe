package com.example.pettracker.service;


import com.example.pettracker.entity.User;
import com.example.pettracker.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import jakarta.annotation.PostConstruct;

@Service
public class NotificationService {

    @Value("${twilio.account-sid:}")
    private String twilioSid;

    @Value("${twilio.auth-token:}")
    private String twilioToken;

    @Value("${twilio.phone-number:}")
    private String twilioNumber;

    private final JavaMailSender mailSender;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public NotificationService(ObjectProvider<JavaMailSender> mailSenderProvider,
                               SimpMessagingTemplate messagingTemplate,
                               UserRepository userRepository) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        if (!twilioSid.isBlank() && !twilioToken.isBlank()) {
            Twilio.init(twilioSid, twilioToken);
        }
    }

    public void sendSms(String to, String body) {
        try {
            if (twilioSid == null || twilioSid.isEmpty()) return;
            Message.creator(new PhoneNumber(to), new PhoneNumber(twilioNumber), body).create();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void sendEmail(String to, String subject, String body) {
        try {
            if (mailSender == null) return;
            SimpleMailMessage m = new SimpleMailMessage();
            m.setTo(to);
            m.setSubject(subject);
            m.setText(body);
            mailSender.send(m);
        } catch (Exception e) { e.printStackTrace(); }
    }
    @Async("notificationExecutor")
    @Transactional(readOnly = true)
    public void notifyOwner(User owner, String message) {
        if (owner == null) return;
        Long ownerId = owner.getId();
        if (ownerId == null) return;

        User managedOwner = userRepository.findById(ownerId).orElse(null);
        if (managedOwner == null) return;

        notifyOwnerContacts(managedOwner, "Pet Alert", message, message, message);
    }
    @Async("notificationExecutor")
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

        notifyOwnerContacts(
                managedOwner,
                "URGENT: Your Pet '" + petName + "' is Lost",
                smsBody,
                message,
                "Your pet " + petName + " (" + petType + ") has been marked as lost!"
        );
    }

    private void notifyOwnerContacts(User owner, String emailSubject, String smsBody, String emailBody, String websocketBody) {
        if (owner.getPhone() != null && !owner.getPhone().isBlank()) sendSms(owner.getPhone(), smsBody);
        if (owner.getEmail() != null && !owner.getEmail().isBlank()) sendEmail(owner.getEmail(), emailSubject, emailBody);
        // Send a WebSocket message to owner-specific topic
        try {
            messagingTemplate.convertAndSend("/topic/alerts/" + owner.getId(), websocketBody);
        } catch (Exception e) { e.printStackTrace(); }
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
