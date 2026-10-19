package com.example.pettracker.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TwilioSmsProvider implements SmsProvider {

    @Value("${twilio.account-sid:}")
    private String twilioSid;

    @Value("${twilio.auth-token:}")
    private String twilioToken;

    @Value("${twilio.phone-number:}")
    private String twilioNumber;

    @Value("${twilio.whatsapp-enabled:false}")
    private boolean twilioWhatsappEnabled;

    @Value("${twilio.whatsapp-number:}")
    private String twilioWhatsappNumber;

    @PostConstruct
    public void init() {
        if (!hasText(twilioSid) || !hasText(twilioToken)) {
            log.warn("Twilio notifications disabled: missing account SID or auth token");
            return;
        }

        Twilio.init(twilioSid, twilioToken);
        log.info("Twilio SMS provider enabled");
    }

    @Override
    public void sendSms(String to, String body) {
        try {
            if (!hasText(to) || !hasText(body)) {
                log.warn("SMS skipped: destination or body missing");
                return;
            }
            if (!isSmsConfigured()) {
                log.warn("SMS skipped for {}: Twilio SMS is not fully configured", to);
                return;
            }

            log.info("Sending SMS via Twilio to {} from {}", to, twilioNumber);
            Message.creator(new PhoneNumber(to), new PhoneNumber(twilioNumber), body).create();
        } catch (Exception ex) {
            log.warn("Failed to send SMS to {}: {}", to, ex.getMessage(), ex);
        }
    }

    @Override
    public void sendWhatsApp(String to, String body) {
        try {
            if (!hasText(to) || !hasText(body)) {
                log.warn("WhatsApp skipped: destination or body missing");
                return;
            }
            if (!isWhatsAppConfigured()) {
                log.warn("WhatsApp skipped for {}: Twilio WhatsApp is not fully configured", to);
                return;
            }

            Message.creator(
                    new PhoneNumber(toWhatsAppAddress(to)),
                    new PhoneNumber(toWhatsAppAddress(twilioWhatsappNumber)),
                    body
            ).create();
        } catch (Exception ex) {
            log.warn("Failed to send WhatsApp message to {}: {}", to, ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isSmsConfigured() {
        return hasText(twilioSid) && hasText(twilioToken) && hasText(twilioNumber);
    }

    @Override
    public boolean isWhatsAppConfigured() {
        return twilioWhatsappEnabled && hasText(twilioSid) && hasText(twilioToken) && hasText(twilioWhatsappNumber);
    }

    private String toWhatsAppAddress(String phoneNumber) {
        if (phoneNumber.startsWith("whatsapp:")) {
            return phoneNumber;
        }
        return "whatsapp:" + phoneNumber;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
