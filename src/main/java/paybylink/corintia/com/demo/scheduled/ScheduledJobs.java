package paybylink.corintia.com.demo.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import paybylink.corintia.com.demo.config.ConfigUtility;
import paybylink.corintia.com.demo.model.MerchantMapping;
import paybylink.corintia.com.demo.model.PaymentNotification;
import paybylink.corintia.com.demo.service.MerchantMappingServiceImpl;
import paybylink.corintia.com.demo.service.PaymentNotificationServiceImpl;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ScheduledJobs {
    private final String STATUS_PENDING = "PENDING";
    private final String STATUS_SUCCESS = "SUCCESS";


    private static final Logger logger = LoggerFactory.getLogger(ScheduledJobs.class);

    private final JavaMailSender javaMailSender;

    @Autowired
    private ConfigUtility configUtil;

    private final PaymentNotificationServiceImpl paymentNotificationService;

    private final MerchantMappingServiceImpl merchantMappingService;

    public ScheduledJobs(JavaMailSender javaMailSender, PaymentNotificationServiceImpl paymentNotificationService, MerchantMappingServiceImpl merchantMappingService) {
        this.javaMailSender = javaMailSender;
        this.paymentNotificationService = paymentNotificationService;
        this.merchantMappingService = merchantMappingService;
    }

    @Scheduled(cron = "0 0/10 * * * *")
    @Async
    public void resendPendingEmails() throws InterruptedException {
        List<PaymentNotification> paymentNotifications = new ArrayList<>();

        try {
            paymentNotifications = paymentNotificationService.findByStatusAndEventCodeAndPblGenerated(STATUS_PENDING, "AUTHORISATION", true);

        } catch (Exception ex) {
            logger.error("Error while fetching paymentNotifications from database.");
        }

        for(PaymentNotification notif : paymentNotifications) {
            DecimalFormat df = new DecimalFormat("0.00");

            MerchantMapping merchantMapping = merchantMappingService.findByMerchantID(notif.getMerchantAccountCode());
            if(merchantMapping == null) {
                merchantMapping = new MerchantMapping();
                merchantMapping.setCcEmails("");
                merchantMapping.setPropertyCode("");
            }

            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(configUtil.getProperty("toEmail"));
            mailMessage.setFrom(configUtil.getProperty("fromEmail"));
            mailMessage.setSubject("--PAYMENT–");

            StringBuilder sb = new StringBuilder();

            if(notif.getAmount() == null) notif.setAmount(new Long(0));

            sb.append("{\"paymentNotification\":{\n" +
                    "\t\"eventCode\": \"" + notif.getEventCode() + "\",\n" +
                    "\t\"successYN\": \"" + successToString(notif.getSuccessYN()) + "\",\n" +
                    "\t\"eventDate\": \" " + notif.getEventDate() + " \",\n" +
                    "\t\"pspReference\": \"" + notif.getPspReference() + "\",\n" +
                    "\t\"hotel\": \"" + merchantMapping.getPropertyCode() + "\",\n" +
                    "\t\"merchantReference\": \"" + notif.getMerchantReference() + "\",\n" +
                    "\t\"amount\": " + df.format(notif.getAmount().doubleValue()/100) + ",\n" +
                    "\t\"first\": \"" + notif.getFirst() + "\",\n" +
                    "\t\"last\": \"" + notif.getLast() + "\", \n" +
                    "\t\"title\": \"" + notif.getTitle() + "\", \n" +
                    "\t\"currency\": \"" + notif.getCurrency() + "\",\n" +
                    "\t\"ccEmail\": \"" + merchantMapping.getCcEmails() + "\",\n" +
                    "\t\"responsysTemplate\": \"TEST_PAYMENT_NOTIFICATION\",\n" +
                    "\t\"shopperEmail\" : \"" + notif.getEmail() + "\"\n" +
                    "}}");

            mailMessage.setText(sb.toString());

            Instant start = Instant.now(); /// before email send

            //if(item.getEventCode().equals("AUTHORISATION") && item.isSuccess()) {
            try {
                logger.info("Webhook fired: TRYING TO SEND REPROCESSING EMAIL");
                logger.info(sb.toString());
                javaMailSender.send(mailMessage);
                notif.setStatus(STATUS_SUCCESS);
                paymentNotificationService.savePaymentNotification(notif);
                sb.setLength(0);
            } catch (Exception ex) {
                logger.error("SMTP Authentification Failed. E-mail was not delivered.");
                logger.error(ex.getMessage());
                notif.setStatus(STATUS_PENDING);
                paymentNotificationService.savePaymentNotification(notif);
            }

            //}after email send
            Instant end = Instant.now();
            Duration timeElapsed = Duration.between(start, end);
            logger.info("TIME TAKEN FOR REPROCESSING EMAIL SEND: "+ timeElapsed.toMillis() +" milliseconds");
        }
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
