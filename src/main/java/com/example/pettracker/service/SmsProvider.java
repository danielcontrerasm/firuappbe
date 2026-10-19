package com.example.pettracker.service;

public interface SmsProvider {

    void sendSms(String to, String body);

    void sendWhatsApp(String to, String body);

    boolean isSmsConfigured();

    boolean isWhatsAppConfigured();
}
