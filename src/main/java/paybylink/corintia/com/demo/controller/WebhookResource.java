package paybylink.corintia.com.demo.controller;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.util.HMACValidator;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import paybylink.corintia.com.demo.async.EmailSender;
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

    private final EmailSender emailSender;

    private final String STATUS_IGNORED = "IGNORED";
    private final String STATUS_PENDING = "PENDING";
    private final String STATUS_SUCCESS = "SUCCESS";


    public String getHMAC() {
        return HMAC;
    }

    private final PaymentNotificationServiceImpl paymentNotificationService;
    private final MerchantMappingServiceImpl merchantMappingService;

    public WebhookResource(EmailSender emailSender, PaymentNotificationServiceImpl paymentNotificationService, MerchantMappingServiceImpl merchantMappingService) {
        this.emailSender = emailSender;
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
                        if (hmacValidator.validateHMAC(item, getHMAC())) {

                            MerchantMapping merchantMapping = merchantMappingService.findByMerchantID(item.getMerchantAccountCode());
                            if(merchantMapping == null) {
                                merchantMapping = new MerchantMapping();
                                merchantMapping.setCcEmails("");
                                merchantMapping.setPropertyCode("");
                                merchantMapping.setTemplateName("");
                            }

                            PaymentNotification paymentNotification  = new PaymentNotification();

                            try {
                                paymentNotification = paymentNotificationService.findByMerchantReference(item.getMerchantReference());
                                if(paymentNotification == null || paymentNotification.getPblGenerated() == null || paymentNotification.getPblGenerated() == false) {
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
                            if(paymentNotification.getSuccessYN() == null || paymentNotification.getSuccessYN() == false) {
                                paymentNotification.setStatus(STATUS_IGNORED);
                            }
                            paymentNotification.setEventDate(new Timestamp(item.getEventDate().getTime()));
                            paymentNotification.setMerchantAccountCode(item.getMerchantAccountCode());
                            paymentNotification.setMerchantReference(item.getMerchantReference());
                            paymentNotification.setPspReference(item.getPspReference());
                            paymentNotification.setCurrency(item.getAmount().getCurrency());
                            paymentNotification.setPayload(new Gson().toJson(notificationRequest));
                            paymentNotification.setEmail(item.getAdditionalData().get("shopperEmail"));
                            paymentNotification.setAmount(item.getAmount().getValue());
                            paymentNotificationService.savePaymentNotification(paymentNotification);

                            DecimalFormat df = new DecimalFormat("0.00");

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
                                    "\t\"amount\": " + df.format(paymentNotification.getAmount().doubleValue()/100) + ",\n" +
                                    "\t\"first\": \"" + paymentNotification.getFirst() + "\",\n" +
                                    "\t\"last\": \"" + paymentNotification.getLast() + "\", \n" +
                                    "\t\"title\": \"" + paymentNotification.getTitle() + "\", \n" +
                                    "\t\"currency\": \"" + paymentNotification.getCurrency() + "\",\n" +
                                    "\t\"ccEmail\": \"" + merchantMapping.getCcEmails() + "\",\n" +
                                    "\t\"responsysTemplate\": \"" + merchantMapping.getTemplateName() + "\",\n" +
                                    "\t\"shopperEmail\" : \"" + item.getAdditionalData().get("shopperEmail") + "\"\n" +
                                    "}}");

                            mailMessage.setText(sb.toString());


                            logger.info("WEBHOOK FIRED");
                            logger.info(sb.toString());
                            if(paymentNotification.getSuccessYN() != null && paymentNotification.getSuccessYN() == true && paymentNotification.getStatus() !=null && !paymentNotification.getStatus().equals(STATUS_IGNORED) && paymentNotification.getEventCode() != null && paymentNotification.getEventCode().equals("AUTHORISATION")) {
                                emailSender.asyncMailSend(mailMessage, paymentNotification);
                            }
                            sb.setLength(0);

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