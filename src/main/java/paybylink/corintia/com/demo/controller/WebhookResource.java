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

@RestController
@RequestMapping("/api")
public class WebhookResource {

    private static final Logger logger = LoggerFactory.getLogger(WebhookResource.class);

    StringBuilder sb = new StringBuilder();

    @Autowired
    private ConfigUtility configUtil;


    private final String HMAC = "7586E4E62A2B70BDC3CB70EB1CCD659E2AB6444EC23E67E04E5E048E88DA8860";
    private final JavaMailSender javaMailSender;


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

        notificationRequest.getNotificationItems().forEach(
                item -> {

                    // We recommend validate HMAC signature in the webhooks for security reasons
                    try {
                        if (true || new HMACValidator().validateHMAC(item, getHMAC())) {

                            MerchantMapping merchantMapping = merchantMappingService.findByMerchantID(item.getMerchantAccountCode());
                            if(merchantMapping == null) {
                                merchantMapping = new MerchantMapping();
                                merchantMapping.setCcEmails("");
                                merchantMapping.setPropertyCode("");
                            }

                            PaymentNotification paymentNotification = paymentNotificationService.findByMerchantReference(item.getMerchantReference());
                            if(paymentNotification == null ) {
                                paymentNotification = new PaymentNotification();
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

                            //if(item.getEventCode().equals("AUTHORISATION") && item.isSuccess()) {
                                javaMailSender.send(mailMessage);
                            //}

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

        logger.info("Webhook fired: ");
        logger.info(sb.toString());
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