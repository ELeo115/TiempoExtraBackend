package com.example.tiempoextrabackend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {
        "http://localhost:5500", "http://127.0.0.1:5500",
        "http://localhost:5501", "http://127.0.0.1:5501"
}, allowCredentials = "true")
public class AdminExtrasController {
    private final JdbcTemplate jdbc;
    public AdminExtrasController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record EstadoDatos(String estado) {}

    private long admin(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s == null || !(s.getAttribute("id_usuario") instanceof Number id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesion como administrador");
        }
        long usuarioId = id.longValue();
        Integer n = jdbc.queryForObject("""
            SELECT COUNT(*) FROM USUARIOS
            WHERE ID_USUARIO = ? AND ROL = 'Administrador' AND ESTADO = 'Activo'
            """, Integer.class, usuarioId);
        if (n == null || n == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo administradores");
        }
        return usuarioId;
    }

    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuarios(HttpServletRequest request) {
        admin(request);
        var usuarios = jdbc.queryForList("""
            SELECT ID_USUARIO AS "id_usuario", NOMBRE_COMPLETO AS "nombre_completo",
                   CORREO AS "correo", ROL AS "rol", ESTADO AS "estado",
                   TO_CHAR(FECHA_REGISTRO, 'YYYY-MM-DD') AS "fecha_registro"
            FROM USUARIOS ORDER BY ID_USUARIO
            """);
        for (var usuario : usuarios) {
            usuario.put("telefonos", jdbc.queryForList("""
                SELECT ID_TELEFONO AS "id_telefono", ID_USUARIO AS "id_usuario",
                       NUMERO AS "numero", TIPO_TELEFONO AS "tipo_telefono",
                       ESTADO AS "estado"
                FROM TELEFONOS_USUARIO
                WHERE ID_USUARIO = ? AND ESTADO = 'Activo'
                ORDER BY ID_TELEFONO
                """, usuario.get("id_usuario")));
        }
        return usuarios;
    }

    @PutMapping("/usuarios/{id}/estado")
    public Map<String, Object> cambiarUsuario(@PathVariable Long id,
                                                @RequestBody EstadoDatos d,
                                                HttpServletRequest request) {
        long adminId = admin(request);
        if (!"Activo".equals(d.estado()) && !"Inactivo".equals(d.estado())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado invalido");
        }
        if (id == adminId && "Inactivo".equals(d.estado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes inactivar tu propia cuenta");
        }
        int n = jdbc.update("UPDATE USUARIOS SET ESTADO = ? WHERE ID_USUARIO = ?", d.estado(), id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        return Map.of("id_usuario", id, "estado", d.estado());
    }

    @GetMapping("/reportes/uso")
    public List<Map<String, Object>> reporteUso(HttpServletRequest request) {
        admin(request);
        return jdbc.queryForList("""
            SELECT c.ID_CANCHA AS "id_cancha", c.NOMBRE_CANCHA AS "nombre_cancha",
                   COUNT(r.ID_RESERVA) AS "reservas_vigentes",
                   COUNT(r.ID_RESERVA) * c.TARIFA_HORA AS "ingreso_estimado"
            FROM CANCHAS c
            LEFT JOIN RESERVAS r ON r.ID_CANCHA = c.ID_CANCHA
                AND r.ESTADO = 'Activo' AND r.ESTADO_RESERVA <> 'Cancelada'
            WHERE c.ESTADO = 'Activo'
            GROUP BY c.ID_CANCHA, c.NOMBRE_CANCHA, c.TARIFA_HORA
            ORDER BY c.NOMBRE_CANCHA
            """);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("mensaje", e.getReason() == null ? "Error" : e.getReason()));
    }
}
