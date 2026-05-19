package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.service.ContabilidadService;
import com.sepa1914.adminservice.service.SepaService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

@Component
public class RemesaController {

    private final SepaService sepaService;
    private final ContabilidadService contabilidadService;

    public RemesaController(SepaService sepaService, ContabilidadService contabilidadService) {
        this.sepaService = sepaService;
        this.contabilidadService = contabilidadService;
    }

    @FXML
    public void alPulsarGenerar(Comunidad comunidad, List<Vecino> vecinos, LocalDate fecha) {
        // 1. CREAR EL DIÁLOGO (MODAL) POR CÓDIGO
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Configuración de Remesa - SEPA 1914");
        dialog.setHeaderText("Comunidad: " + comunidad.getNombre());

        // Botones de Aceptar y Cancelar
        ButtonType btnGenerar = new ButtonType("Generar Remesa", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnGenerar, ButtonType.CANCEL);

        // --- CONTENIDOS DEL MODAL ---
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));

        // A. Tipo de Remesa
        Label lblTipo = new Label("Tipo de Remesa:");
        RadioButton rbOrdinaria = new RadioButton("Ordinaria");
        RadioButton rbExtra = new RadioButton("Extraordinaria");
        ToggleGroup grupoTipo = new ToggleGroup();
        rbOrdinaria.setToggleGroup(grupoTipo);
        rbExtra.setToggleGroup(grupoTipo);
        rbOrdinaria.setSelected(true);

        // B. Nombre/Etiqueta (Solo para Extraordinarias)
        TextField txtEtiqueta = new TextField();
        txtEtiqueta.setPromptText("Ej: Pintura Fachada");
        txtEtiqueta.setDisable(true); // Desactivado por defecto

        // Activar/Desactivar cuadro de texto según elección
        rbExtra.setOnAction(e -> txtEtiqueta.setDisable(false));
        rbOrdinaria.setOnAction(e -> { txtEtiqueta.setDisable(true); txtEtiqueta.clear(); });

        // C. Acción: ¿Nueva o Modificar?
        Label lblAccion = new Label("¿Qué desea hacer?");
        RadioButton rbNueva = new RadioButton("Crear como remesa NUEVA (No borra nada)");
        RadioButton rbModificar = new RadioButton("MODIFICAR la anterior (Borra recibos/asientos previos)");
        ToggleGroup grupoAccion = new ToggleGroup();
        rbNueva.setToggleGroup(grupoAccion);
        rbModificar.setToggleGroup(grupoAccion);
        rbNueva.setSelected(true);

        layout.getChildren().addAll(lblTipo, rbOrdinaria, rbExtra, new Label("Identificador (si es extra):"), txtEtiqueta,
                new Separator(), lblAccion, rbNueva, rbModificar);
        dialog.getDialogPane().setContent(layout);

        // 2. MOSTRAR MODAL Y PROCESAR RESULTADO
        dialog.showAndWait().ifPresent(response -> {
            if (response == btnGenerar) {
                String tipo = rbOrdinaria.isSelected() ? "ORDINARIA" : "EXTRAORDINARIA";
                String etiqueta = txtEtiqueta.getText();
                boolean esModificacion = rbModificar.isSelected();

                try {
                    // --- LÓGICA DE BORRADO SI ES MODIFICACIÓN ---
                    if (esModificacion) {
                        System.out.println("GTI: Borrando datos anteriores para regenerar...");
                        contabilidadService.borrarRecibosYcontabilidadDelMes(
                                comunidad.getId(),
                                fecha.getMonthValue(),
                                fecha.getYear(),
                                tipo,
                                etiqueta,
                                true // Mandamos true para que ejecute el borrado
                        );
                    }

                    // --- GENERAR NUEVA REMESA ---
                    String resultado = sepaService.generarCuaderno19(
                            comunidad, vecinos, fecha, tipo, etiqueta, esModificacion
                    );

                    Alert exito = new Alert(Alert.AlertType.INFORMATION, "Remesa generada con éxito.");
                    exito.show();

                } catch (Exception e) {
                    Alert error = new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage());
                    error.show();
                }
            }
        });
    }
}