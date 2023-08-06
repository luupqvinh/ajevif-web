package org.ajevif.web;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;
import software.amazon.awssdk.utils.StringUtils;

public class AjevifRequestHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final Logger logger = LoggerFactory.getLogger(AjevifRequestHandler.class);

    private static final Gson gson = new GsonBuilder().create();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {

        logger.info("input {}", gson.toJson(input));

        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();

        String body = input.getBody();

        if (body == null || body.trim().length() == 0) {
            logger.error("Body is empty!");

            response.setStatusCode(400);
            IllegalArgumentException exception = new IllegalArgumentException("[BadRequest] Body vide");
            response.setBody(gson.toJson(exception));

            return response;
        }

        ContactDto dto = gson.fromJson(body, ContactDto.class);
        if (StringUtils.isBlank(dto.nom) || StringUtils.isBlank(dto.telephone) || StringUtils.isBlank(dto.message)) {
            response.setStatusCode(400);
            IllegalArgumentException exception = new IllegalArgumentException("[BadRequest] nom ou telephone ou message absent");
            response.setBody(gson.toJson(exception));
            return response;
        }

        boolean ok = sendEmail(dto.nom, dto.telephone, dto.message);
        response.setStatusCode(ok ? 200 : 500);

        return response;
    }


    private boolean sendEmail(String nom, String telephone, String message) {

        SesV2Client sesv2Client = SesV2Client.builder()
                .region(Region.EU_WEST_3)
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();


        String subject = String.format("Mail de '%s' envoyé depuis le site ajevif.org", nom);

        StringBuilder bodyHTML = new StringBuilder();
        bodyHTML.append("<html>")
                .append("<head></head>")
                .append("<body>")
                .append(String.format("<p>Nom: %s</p>", nom))
                .append(String.format("<p>Téléphone: %s</p>", telephone))
                .append(String.format("<p>%s</p>", message))
                .append("</body>")
                .append("</html>");

        return sendEmail(sesv2Client, "inscription@ajevif.org", subject, bodyHTML.toString());

    }


    private boolean sendEmail(SesV2Client sesv2Client, String recipient, String subject, String bodyHtml) {

        Destination destination = Destination.builder()
                .toAddresses(recipient)
                .build();

        Content content = Content.builder()
                .data(bodyHtml)
                .build();

        Content sub = Content.builder()
                .data(subject)
                .build();

        Body body = Body.builder()
                .html(content)
                .build();

        software.amazon.awssdk.services.sesv2.model.Message msg = Message.builder()
                .subject(sub)
                .body(body)
                .build();

        EmailContent emailContent = EmailContent.builder()
                .simple(msg)
                .build();

        SendEmailRequest emailRequest = SendEmailRequest.builder()
                .destination(destination)
                .content(emailContent)
                .fromEmailAddress("ne-pas-repondre@ajevif.org")
                .build();

        try {
            logger.info("sendEmail to {}...", recipient);
            sesv2Client.sendEmail(emailRequest);
            logger.info("sendEmail DONE.");
            return true;
        } catch (SesV2Exception e) {
            logger.error("Failed to sendEmail", e);
            return false;
        }

    }
}
