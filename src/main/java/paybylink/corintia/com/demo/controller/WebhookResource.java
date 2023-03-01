package paybylink.corintia.com.demo.controller;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.util.HMACValidator;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import paybylink.corintia.com.demo.config.ConfigUtility;
import paybylink.corintia.com.demo.model.MerchantMapping;
import paybylink.corintia.com.demo.model.PaymentNotification;
import paybylink.corintia.com.demo.service.MerchantMappingServiceImpl;
import paybylink.corintia.com.demo.service.PaymentNotificationServiceImpl;

import javax.servlet.http.HttpServletRequest;
import java.security.SignatureException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api")
public class WebhookResource {

    private static final Logger logger = LoggerFactory.getLogger(WebhookResource.class);

    HMACValidator hmacValidator = new HMACValidator();

    @Autowired
    private ConfigUtility configUtil;

    private final String HMAC = "5ED93809DCC4F371013F5A12A7AED204A67110DAAEA4B55459CD3385522B37C2";
    private final JavaMailSender javaMailSender;

    private final String STATUS_IGNORED = "IGNORED";
    private final String STATUS_PENDING = "PENDING";
    private final String STATUS_SUCCESS = "SUCCESS";


    public String getHMAC() {
        return HMAC;
    }

    private final PaymentNotificationServiceImpl paymentNotificationService;
    private final MerchantMappingServiceImpl merchantMappingService;

    public WebhookResource(JavaMailSender javaMailSender, PaymentNotificationServiceImpl paymentNotificationService, MerchantMappingServiceImpl merchantMappingService) {
        this.javaMailSender = javaMailSender;
        this.paymentNotificationService = paymentNotificationService;
        this.merchantMappingService = merchantMappingService;
    }

    /**
     * Process incoming Webhook notifications
     * @param notificationRequest
     * @return
     */
    @PostMapping("/webhooks/notifications")
    public ResponseEntity<String> webhooks(@RequestBody NotificationRequest notificationRequest){

        Instant startWebhook = Instant.now(); /// START OF WEBHOOK
        notificationRequest.getNotificationItems().forEach(
                item -> {

                    logger.info("ADYEN ITEMS LOOP " + item.getPspReference());

                    try {
                        if (new HMACValidator().validateHMAC(item, getHMAC())) {

                            MerchantMapping merchantMapping = merchantMappingService.findByMerchantID(item.getMerchantAccountCode());
                            if(merchantMapping == null) {
                                merchantMapping = new MerchantMapping();
                                merchantMapping.setCcEmails("");
                                merchantMapping.setPropertyCode("");
                            }

                            PaymentNotification paymentNotification  = new PaymentNotification();

                            try {
                                paymentNotification = paymentNotificationService.findByMerchantReference(item.getMerchantReference());
                                if(paymentNotification == null || (paymentNotification.getPblGenerated() != null && paymentNotification.getPblGenerated() != true)) {
                                    paymentNotification = new PaymentNotification();
                                    paymentNotification.setStatus(STATUS_IGNORED);
                                } else {
                                    paymentNotification.setStatus(STATUS_PENDING);
                                }
                            } catch (Exception ex) {
                                logger.error("Error while fetching paymentNotification from database.");
                            }


                            paymentNotification.setEventCode(item.getEventCode());
                            paymentNotification.setSuccessYN(item.isSuccess());
                            paymentNotification.setEventDate(new Timestamp(item.getEventDate().getTime()));
                            paymentNotification.setMerchantAccountCode(item.getMerchantAccountCode());
                            paymentNotification.setMerchantReference(item.getMerchantReference());
                            paymentNotification.setPspReference(item.getPspReference());
                            paymentNotification.setAmount(item.getAmount().getValue());
                            paymentNotification.setCurrency(item.getAmount().getCurrency());
                            paymentNotification.setPayload(new Gson().toJson(notificationRequest));
                            paymentNotification.setEmail(item.getAdditionalData().get("shopperEmail"));

                            paymentNotificationService.savePaymentNotification(paymentNotification);

                            DecimalFormat df = new DecimalFormat("#.00");

                            SimpleMailMessage mailMessage = new SimpleMailMessage();
                            mailMessage.setTo(configUtil.getProperty("toEmail"));
                            mailMessage.setFrom(configUtil.getProperty("fromEmail"));
                            mailMessage.setSubject("--PAYMENT–");

                            StringBuilder sb = new StringBuilder();

                            sb.append("{\"paymentNotification\":{\n" +
                                    "\t\"eventCode\": \"" + item.getEventCode() + "\",\n" +
                                    "\t\"successYN\": \"" + successToString(paymentNotification.getSuccessYN()) + "\",\n" +
                                    "\t\"eventDate\": \" " + paymentNotification.getEventDate() + " \",\n" +
                                    "\t\"pspReference\": \"" + paymentNotification.getPspReference() + "\",\n" +
                                    "\t\"hotel\": \"" + merchantMapping.getPropertyCode() + "\",\n" +
                                    "\t\"merchantReference\": \"" + paymentNotification.getMerchantReference() + "\",\n" +
                                    "\t\"amount\": " + df.format(paymentNotification.getAmount()/100) + ",\n" +
                                    "\t\"first\": \"" + paymentNotification.getFirst() + "\",\n" +
                                    "\t\"last\": \"" + paymentNotification.getLast() + "\", \n" +
                                    "\t\"title\": \"" + paymentNotification.getTitle() + "\", \n" +
                                    "\t\"currency\": \"" + paymentNotification.getCurrency() + "\",\n" +
                                    "\t\"ccEmail\": \"" + merchantMapping.getCcEmails() + "\",\n" +
                                    "\t\"responsysTemplate\": \"TEST_PAYMENT_NOTIFICATION\",\n" +
                                    "\t\"shopperEmail\" : \"" + item.getAdditionalData().get("shopperEmail") + "\"\n" +
                                    "}}");

                            mailMessage.setText(sb.toString());

                            Instant start = Instant.now(); /// before email send

                            //if(item.getEventCode().equals("AUTHORISATION") && item.isSuccess()) {
                            try {
                                logger.info("Webhook fired: TRYING TO SEND EMAIL");
                                logger.info(sb.toString());
                                if(paymentNotification.getStatus() !=null && !paymentNotification.getStatus().equals(STATUS_IGNORED) && paymentNotification.getEventCode() != null && paymentNotification.getEventCode().equals("AUTHORISATION")) {
                                    javaMailSender.send(mailMessage);
                                    paymentNotification.setStatus(STATUS_SUCCESS);
                                }
                                paymentNotificationService.savePaymentNotification(paymentNotification);
                                sb.setLength(0);
                            } catch (Exception ex) {
                                logger.error("SMTP Authentification Failed. E-mail was not delivered.");
                                logger.error(ex.getMessage());
                                paymentNotification.setStatus(STATUS_PENDING);
                                paymentNotificationService.savePaymentNotification(paymentNotification);
                            }

                            //}after email send
                            Instant end = Instant.now();
                            Duration timeElapsed = Duration.between(start, end);
                            logger.info("TIME TAKEN FOR EMAIL SEND: "+ timeElapsed.toMillis() +" milliseconds");

                        } else {
                            // invalid HMAC signature: do not send [accepted] response
                            System.out.println("INVALID HMC");
                            throw new RuntimeException("Invalid HMAC signature");
                        }
                    } catch (SignatureException e) {
                        System.out.println("Error while validating HMAC Key");
                    }
                }

        );

        // Notifying the server we're accepting the payload


        Instant endWebhook = Instant.now(); /// END OF WEBHOOK
        Duration timeElapsed = Duration.between(startWebhook, endWebhook);
        logger.info("TIME TAKEN FOR WEBHOOK PROCESS: "+ timeElapsed.toMillis() +" milliseconds");

        return ResponseEntity.ok().body("[accepted]");
    }


    /**
     * Process incoming Webhook notifications
     * @return
     */
    @PostMapping("/webhooks/notificationstest")
    public ResponseEntity<String> webhookstest(HttpServletRequest request){


        return ResponseEntity.ok().body("[accepted]");
    }

    String successToString(Boolean success) {
        if (success == true) {
            return "Y";
        } else if (success == false) {
            return "N";
        }
        return "";
    }

}