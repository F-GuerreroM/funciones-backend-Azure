package com.duoc.cloud;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;

public class GeneradorEventosFunction {

    @FunctionName("GeneradorEventosFunction")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) 
            HttpRequestMessage<String> request,
            final ExecutionContext context) {

        // PEGA AQUÍ TU ENDPOINT Y TU KEY DE AZURE
        String eventGridTopicEndpoint = "https://duoc-eventgrid.eastus-1.eventgrid.azure.net/api/events";
        String eventGridTopicKey = "2ceaIzCtaLNIt7rTX4FFcSh7eFTTkyREGBryRZMIeEc1YKVjg9j7JQQJ99CJACYeBjFXJ3w3AAABAZEGWEv0";

        try {
            EventGridPublisherClient<EventGridEvent> client = new EventGridPublisherClientBuilder()
                    .endpoint(eventGridTopicEndpoint)
                    .credential(new AzureKeyCredential(eventGridTopicKey))
                    .buildEventGridEventPublisherClient();

            EventGridEvent event = new EventGridEvent(
                    "/EventGridEvents/example/source",
                    "Example.EventType",
                    BinaryData.fromObject("Nuevo usuario creado o rol actualizado en el sistema"),
                    "0.1");

            client.sendEvent(event);

            return request.createResponseBuilder(HttpStatus.OK)
                    .body("Evento creado correctamente")
                    .build();
        } catch (Exception e) {
            context.getLogger().severe("Error al publicar evento: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al publicar evento: " + e.getMessage())
                    .build();
        }
    }
}