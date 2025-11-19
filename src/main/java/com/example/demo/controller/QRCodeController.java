package com.example.demo.controller;

import com.example.demo.dto.QRCodeDTO;
import com.example.demo.entity.QRCodeEntity;
import com.example.demo.service.AES256Service;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.QRCodeReaderService;
import com.example.demo.service.QRCodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
// import org.springframework.web.servlet.support.ServletUriComponentsBuilder; // ya no lo usamos

import javax.crypto.SecretKey;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/qr")
@CrossOrigin(origins = "*")
public class QRCodeController {

    private final QRCodeService qrCodeService;
    private final AES256Service aes256Service;
    private final QRCodeReaderService qrCodeReaderService;
    private final FileStorageService fileStorageService;

    // Se usa cuando el backend redirige a una vista local tipo /perfil?ci=...
    @Value("${app.profile.base-url:http://localhost:8080/perfil}")
    private String profileBaseUrl;

    // Base pública (IP o dominio) para la URL que irá DENTRO del QR y para imágenes
    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public QRCodeController(
            QRCodeService qrCodeService,
            AES256Service aes256Service,
            QRCodeReaderService qrCodeReaderService,
            FileStorageService fileStorageService
    ) {
        this.qrCodeService = qrCodeService;
        this.aes256Service = aes256Service;
        this.qrCodeReaderService = qrCodeReaderService;
        this.fileStorageService = fileStorageService;
    }

    /** Construye la URL pública que debe codificarse en el QR.
     *  SIEMPRE usa el TEXTO CIFRADO (encrypted), NO el IV.
     *  Ej.: http://192.168.0.11:8080/qr/profile?encryptedText=BASE64...
     */
    private String buildQrUrl(String encryptedText, String id) {
    // En producción, usar solo ID para evitar QR overflow
    if (publicBaseUrl.contains("railway") || publicBaseUrl.contains("production")) {
        return publicBaseUrl + "/qr/profile?id=" + URLEncoder.encode(id, StandardCharsets.UTF_8);
    } else {
        // En local, mantener comportamiento original
        return publicBaseUrl + "/qr/profile?encryptedText=" + 
               URLEncoder.encode(encryptedText, StandardCharsets.UTF_8);
    }
}

    // =========================
    // 1) GENERAR QR SIN DATOS
    // =========================
    @PostMapping("/generate")
    public ResponseEntity<QRCodeDTO> generateUniqueQR() {
        try {
            String hash = qrCodeService.generateUniqueQRHash();
            SecretKey key = aes256Service.generateKey();
            String keyString = aes256Service.keyToString(key);

            Map<String, String> enc = aes256Service.encryptWithIV(hash, key);
            String encryptedText = enc.get("encrypted"); // <- para URL/mostrar
            String iv = enc.get("iv");                   // <- guardar solamente

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String id = UUID.randomUUID().toString();

            // Guardar en BD
            qrCodeService.saveQRCode(id, hash, encryptedText, keyString, iv, LocalDateTime.now());

            // URL pública a codificar en el QR
            String qrUrl = buildQrUrl(encryptedText, id);

            // Generar PNG del QR con la URL (por si luego quieres usarlo)
            String pngName = "qr_" + id + ".png";
            fileStorageService.generateQRCodeImage(qrUrl, pngName);
            String qrImageUrl = publicBaseUrl + "/uploads/" + pngName; // <- ahora también público

            QRCodeDTO dto = new QRCodeDTO(
                    id, hash, encryptedText, keyString, iv,
                    timestamp, true, "QR generado y guardado exitosamente"
            );
            dto.setQrUrl(qrUrl);
            // si algún día quieres exponer qrImageUrl, podrías extender el DTO

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new QRCodeDTO(
                    null, null, null, null, null, null, false, "Error: " + e.getMessage()
            ));
        }
    }

    // =======================================
    // 2) GENERAR QR CON DATOS PERSONALES + FOTO
    // =======================================
    @PostMapping("/generate-with-data")
    public ResponseEntity<Map<String, Object>> generateQRWithPersonalData(
            @RequestParam("nombre") String nombre,
            @RequestParam("ci") String ci,
            @RequestParam("apellidos") String apellidos,
            @RequestParam("fechaNacimiento") String fechaNacimiento,
            @RequestParam("cargo") String cargo,
            @RequestParam("departamento") String departamento,          // ✅ NUEVO
            @RequestParam("areaVoluntariado") String areaVoluntariado,  // ✅ NUEVO
            @RequestParam("foto") MultipartFile foto
    ) {
        try {
            if (ci == null || ci.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "El CI es obligatorio");
                return ResponseEntity.badRequest().body(error);
            }

            // 1) Guardar foto -> nombre único
            String savedFileName = fileStorageService.storeFile(foto);

            // 2) URL pública de la foto (sirve desde /uploads/**)
            //    🔴 Importante: ya NO usamos localhost, usamos publicBaseUrl
            String fotoUrl = publicBaseUrl + "/uploads/" + savedFileName;

            // 3) Generar hash/cifrado
            String hash = qrCodeService.generateUniqueQRHash();
            SecretKey key = aes256Service.generateKey();
            String keyString = aes256Service.keyToString(key);

            Map<String, String> enc = aes256Service.encryptWithIV(hash, key);
            String encryptedText = enc.get("encrypted"); // <- para URL/mostrar
            String iv = enc.get("iv");                   // <- guardar solamente

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String id = UUID.randomUUID().toString();
            LocalDate fechaNac = LocalDate.parse(fechaNacimiento);

            // 4) Guardar en BD con datos personales + nuevos campos
            qrCodeService.saveQRCodeWithPersonalData(
                    id, hash, encryptedText, keyString, iv, LocalDateTime.now(),
                    nombre, ci, apellidos, fechaNac, fotoUrl, cargo,
                    departamento, areaVoluntariado
            );

            // 5) URL dentro del QR y PNG del QR
            String qrUrl = buildQrUrl(encryptedText, id);
            String pngName = "qr_" + id + ".png";
            fileStorageService.generateQRCodeImage(qrUrl, pngName);

            // ✅ PNG del QR también con base pública
            String qrImageUrl = publicBaseUrl + "/uploads/" + pngName;

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", id);
            response.put("plain_text", hash);
            response.put("encrypted_text", encryptedText);
            response.put("encryption_key", keyString);
            response.put("iv", iv);
            response.put("created_at", timestamp);

            response.put("nombre", nombre);
            response.put("ci", ci);
            response.put("apellidos", apellidos);
            response.put("fecha_nacimiento", fechaNacimiento);
            response.put("foto_url", fotoUrl);
            response.put("cargo", cargo);

            // ✅ Nuevos en la respuesta
            response.put("departamento", departamento);
            response.put("area_voluntariado", areaVoluntariado);

            response.put("qr_url", qrUrl);            // lo que VA DENTRO del QR
            response.put("qr_image_url", qrImageUrl); // PNG público para imprimir/mostrar
            response.put("message", "QR con datos personales generado exitosamente");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // =======================================
    // 3) VERIFICAR (JSON) POR TEXTO CIFRADO
    // =======================================
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyQRCode(@RequestBody Map<String, String> request) {
        try {
            String encryptedText = request.get("encryptedText");
            QRCodeEntity qrCode = qrCodeService.getQRCodeWithPersonalData(encryptedText);

            Map<String, Object> response = new HashMap<>();
            if (qrCode != null) {
                response.put("success", true);
                response.put("exists", true);
                response.put("hasPersonalData", qrCode.getNombre() != null);

                if (qrCode.getNombre() != null) {
                    response.put("nombre", qrCode.getNombre());
                    response.put("ci", qrCode.getCi());
                    response.put("apellidos", qrCode.getApellidos());
                    response.put("fechaNacimiento", qrCode.getFechaNacimiento() != null ?
                            qrCode.getFechaNacimiento().toString() : null);
                    response.put("fotoUrl", qrCode.getFotoUrl());
                    response.put("cargo", qrCode.getCargo());

                    // ✅ incluir nuevos campos
                    response.put("departamento", qrCode.getDepartamento());
                    response.put("areaVoluntariado", qrCode.getAreaVoluntariado());
                    response.put("area_voluntariado", qrCode.getAreaVoluntariado());

                    response.put("message", "✅ QR VÁLIDO - Datos personales encontrados");
                } else {
                    response.put("message", "✅ QR VÁLIDO - Existe en la base de datos");
                }
            } else {
                response.put("success", true);
                response.put("exists", false);
                response.put("hasPersonalData", false);
                response.put("message", "❌ QR NO ENCONTRADO");
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("exists", false);
            response.put("hasPersonalData", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // =======================================
    // 4) LEER CONTENIDO DE QR DESDE IMAGEN
    // =======================================
    @PostMapping("/read-from-image")
    public ResponseEntity<Map<String, Object>> readQRFromImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("content", null);
                errorResponse.put("message", "El archivo está vacío o no se proporcionó");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            String qrContent = qrCodeReaderService.readQRCode(file);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("content", qrContent);
            response.put("message", "QR code leído exitosamente");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("content", null);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // =======================================
    // 5) VERIFICAR DESDE IMAGEN Y DEVOLVER DATOS
    // =======================================
    @PostMapping("/verify-from-image")
    public ResponseEntity<Map<String, Object>> verifyQRFromImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("exists", false);
                errorResponse.put("message", "El archivo está vacío o no se proporcionó");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            String encryptedText = qrCodeReaderService.readQRCode(file);
            QRCodeEntity qrCode = qrCodeService.getQRCodeWithPersonalData(encryptedText);

            Map<String, Object> response = new HashMap<>();
            response.put("encryptedText", encryptedText);

            if (qrCode != null) {
                response.put("success", true);
                response.put("exists", true);
                if (qrCode.getNombre() != null) {
                    response.put("hasPersonalData", true);
                    response.put("nombre", qrCode.getNombre());
                    response.put("ci", qrCode.getCi());
                    response.put("apellidos", qrCode.getApellidos());
                    response.put("fechaNacimiento", qrCode.getFechaNacimiento() != null ?
                            qrCode.getFechaNacimiento().toString() : null);
                    response.put("fotoUrl", qrCode.getFotoUrl());
                    response.put("cargo", qrCode.getCargo());

                    // ✅ nuevos campos también aquí
                    response.put("departamento", qrCode.getDepartamento());
                    response.put("areaVoluntariado", qrCode.getAreaVoluntariado());
                    response.put("area_voluntariado", qrCode.getAreaVoluntariado());

                    response.put("message", "✅ QR VÁLIDO - Datos personales encontrados");
                } else {
                    response.put("hasPersonalData", false);
                    response.put("message", "✅ QR VÁLIDO - Existe en la base de datos");
                }
            } else {
                response.put("success", true);
                response.put("exists", false);
                response.put("hasPersonalData", false);
                response.put("message", "❌ QR NO VÁLIDO - No existe en la base de datos");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("exists", false);
            response.put("hasPersonalData", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // =======================================
    // 6) REDIRECCIÓN POR CI DESDE ENCRYPTEDTEXT (para integraciones internas)
    // =======================================
    @GetMapping("/redirect")
    public void redirectToProfile(@RequestParam("encryptedText") String encryptedText,
                                  HttpServletResponse resp) throws Exception {
        QRCodeEntity qr = qrCodeService.getQRCodeWithPersonalData(encryptedText);
        if (qr == null || qr.getCi() == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "QR no válido o sin datos personales");
            return;
        }
        String ci = URLEncoder.encode(qr.getCi(), StandardCharsets.UTF_8);
        String target = profileBaseUrl.contains("?")
                ? (profileBaseUrl + "&ci=" + ci)
                : (profileBaseUrl + "?ci=" + ci);
        resp.sendRedirect(target);
    }

    // =======================================
    // 7) ABRIR TEXTO CIFRADO CRUDO (QRs antiguos sin URL)
    // =======================================
    @GetMapping("/open")
    public void openRaw(@RequestParam("c") String cipher, HttpServletResponse resp) throws Exception {
        String target = publicBaseUrl + "/qr/profile?encryptedText=" +
                URLEncoder.encode(cipher, StandardCharsets.UTF_8);
        resp.sendRedirect(target);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Servicio QR OK - BD Conectada");
    }
}
