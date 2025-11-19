package com.example.demo.controller;

import com.example.demo.entity.QRCodeEntity;
import com.example.demo.service.QRCodeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/qr")
@CrossOrigin(origins = "*")
public class ProfilePageController {

    private final QRCodeService qrCodeService;

    public ProfilePageController(QRCodeService qrCodeService) {
        this.qrCodeService = qrCodeService;
    }

    // Vista profesional: /qr/profile?encryptedText=...
    @GetMapping("/profile")
public String profileView(
        @RequestParam(value = "encryptedText", required = false) String encryptedText,
        @RequestParam(value = "id", required = false) String id,
        Model model) {

    QRCodeEntity q = null;
    
    // Buscar por ID primero (más eficiente en producción)
    if (id != null && !id.trim().isEmpty()) {
        q = qrCodeService.findById(id).orElse(null);
    }
    
    // Si no se encuentra por ID, buscar por encryptedText (para compatibilidad)
    if (q == null && encryptedText != null && !encryptedText.trim().isEmpty()) {
        q = qrCodeService.getQRCodeWithPersonalData(encryptedText);
    }

    // El resto del código se mantiene igual...
    if (q == null) {
        model.addAttribute("found", false);
        model.addAttribute("message", "QR no válido o no registrado en la base de datos.");
    } else {
        // ... código existente
    }

    model.addAttribute("encryptedText", encryptedText);
    return "qr/profile";
}

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("<", "&lt;").replace(">", "&gt;");
    }
}
