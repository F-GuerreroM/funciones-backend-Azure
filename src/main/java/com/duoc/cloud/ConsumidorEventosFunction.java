package com.duoc.cloud;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import java.util.logging.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class ConsumidorEventosFunction {

    @FunctionName("ProcessEventGridEvent")
    public void run(
            @EventGridTrigger(name = "eventGridEvent") String content,
            final ExecutionContext context) {
        
        Logger logger = context.getLogger();
        logger.info("Funcion con Event Grid trigger ejecutada.");

        // Deserializar el contenido
        Gson gson = new Gson();
        JsonObject eventGridEvent = gson.fromJson(content, JsonObject.class);

        // Logear los detalles del evento
        logger.info("Evento recibido: " + eventGridEvent.toString());

        // Procesar la data del evento
        String eventType = eventGridEvent.get("eventType").getAsString();
        String data = eventGridEvent.get("data").toString();

        logger.info("Tipo de evento: " + eventType);
        logger.info("Data del evento: " + data);
    }
}