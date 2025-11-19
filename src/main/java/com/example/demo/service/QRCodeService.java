package com.example.demo.service;

import com.example.demo.entity.QRCodeEntity;
import com.example.demo.repository.QRCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.Optional;

@Service
public class QRCodeService {

    private final QRCodeRepository qrCodeRepository;

    public QRCodeService(QRCodeRepository qrCodeRepository) {
        this.qrCodeRepository = qrCodeRepository;
    }

    public String generateUniqueQRHash() throws Exception {
        String uuid = UUID.randomUUID().toString();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String rawData = uuid + "-" + timestamp;

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(rawData.getBytes());

        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }

    @Transactional
    public QRCodeEntity saveQRCode(String id, String plainTextHash, String encryptedText,
                                   String encryptionKey, String ivValue, LocalDateTime createdAt) {
        QRCodeEntity qrCode = new QRCodeEntity(id, plainTextHash, encryptedText, encryptionKey, ivValue, createdAt);
        return qrCodeRepository.save(qrCode);
    }

    @Transactional
    public QRCodeEntity saveQRCodeWithPersonalData(String id, String plainTextHash, String encryptedText,
                                                   String encryptionKey, String ivValue, LocalDateTime createdAt,
                                                   String nombre, String ci, String apellidos, LocalDate fechaNacimiento,
                                                   String fotoUrl, String cargo,
                                                   String departamento, String areaVoluntariado) {

        QRCodeEntity qrCode = new QRCodeEntity(
                id, plainTextHash, encryptedText, encryptionKey, ivValue, createdAt,
                nombre, ci, apellidos, fechaNacimiento, fotoUrl, cargo,
                departamento, areaVoluntariado
        );
        return qrCodeRepository.save(qrCode);
    }

    public boolean verifyQRCode(String encryptedText) {
        return qrCodeRepository.findByEncryptedText(encryptedText).isPresent();
    }

    public QRCodeEntity getQRCodeWithPersonalData(String encryptedText) {
        return qrCodeRepository.findByEncryptedText(encryptedText).orElse(null);
    }

    public boolean verifyByPlainHash(String plainHash) {
        return qrCodeRepository.findByPlainTextHash(plainHash).isPresent();
    }

    public Long getTotalGeneratedCount() {
        return qrCodeRepository.countActiveQRCodes();
    }
    public Optional<QRCodeEntity> findById(String id) {
    return qrCodeRepository.findById(id);
}
}
