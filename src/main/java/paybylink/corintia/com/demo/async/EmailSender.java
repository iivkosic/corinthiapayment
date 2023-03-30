package paybylink.corintia.com.demo.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import paybylink.corintia.com.demo.config.ConfigUtility;
import paybylink.corintia.com.demo.model.PaymentNotification;
import paybylink.corintia.com.demo.service.PaymentNotificationServiceImpl;

@Service
public class EmailSender {

    private final String STATUS_PENDING = "PENDING";

    private final String STATUS_SUCCESS = "SUCCESS";

    private final PaymentNotificationServiceImpl paymentNotificationService;

    @Autowired
    private ConfigUtility configUtil;
    private static final Logger logger = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender javaMailSender;

    public EmailSender(PaymentNotificationServiceImpl paymentNotificationService, JavaMailSender javaMailSender) {
        this.paymentNotificationService = paymentNotificationService;
        this.javaMailSender = javaMailSender;
    }

    @Async
    public void asyncMailSend(SimpleMailMessage mailMessage, PaymentNotification paymentNotification) {
        try {
            logger.info("SENDING EMAIL ASYNCHRONOUSLY");
            javaMailSender.send(mailMessage);
            paymentNotification.setStatus(STATUS_SUCCESS);
            paymentNotificationService.savePaymentNotification(paymentNotification);
        } catch (Exception ex) {
            logger.error("SMTP Authentification Failed. E-mail was not delivered.");
            logger.error(ex.getMessage());
            paymentNotification.setStatus(STATUS_PENDING);
            paymentNotificationService.savePaymentNotification(paymentNotification);
        }
    }

}
