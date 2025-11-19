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
    
    // En producción, priorizar ID sobre encryptedText
    if (id != null && !id.trim().isEmpty()) {
        q = qrCodeService.findById(id).orElse(null);
    }
    
    // Si no se encuentra por ID, intentar con encryptedText
    if (q == null && encryptedText != null && !encryptedText.trim().isEmpty()) {
        q = qrCodeService.getQRCodeWithPersonalData(encryptedText);
    }

    if (q == null) {
        model.addAttribute("found", false);
        model.addAttribute("message", "QR no válido o no registrado en la base de datos.");
    } else {
        model.addAttribute("found", true);
        model.addAttribute("nombre", safe(q.getNombre()));
        model.addAttribute("apellidos", safe(q.getApellidos()));
        model.addAttribute("ci", safe(q.getCi()));
        model.addAttribute("fechaNacimiento", q.getFechaNacimiento() != null ? 
            q.getFechaNacimiento().toString() : "");
        model.addAttribute("fotoUrl", q.getFotoUrl());
        model.addAttribute("cargo", safe(q.getCargo()));
        model.addAttribute("departamento", safe(q.getDepartamento()));
        model.addAttribute("areaVoluntariado", safe(q.getAreaVoluntariado()));
        model.addAttribute("message", "✅ QR VÁLIDO - Datos verificados en la base de datos");
    }

    model.addAttribute("encryptedText", encryptedText);
    return "qr/profile";
}

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("<", "&lt;").replace(">", "&gt;");
    }
}
