package paybylink.corintia.com.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.mail.SimpleMailMessage;
import paybylink.corintia.com.demo.config.ConfigUtility;
import paybylink.corintia.com.demo.model.PaymentNotification;
import paybylink.corintia.com.demo.service.PaymentNotificationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

@Controller
public class Web {

    private static final Logger logger = LoggerFactory.getLogger(Web.class);


    @Autowired
    private ConfigUtility configUtil;

    private final JavaMailSender javaMailSender;
    private final PaymentNotificationServiceImpl paymentNotificationService;


    public Web(JavaMailSender javaMailSender, PaymentNotificationServiceImpl paymentNotificationService) {
        this.javaMailSender = javaMailSender;
        this.paymentNotificationService = paymentNotificationService;
    }

    @RequestMapping({"/", "/index"})
    public String paymentIndex(Model model,
                            HttpSession session, @RequestParam(value = "lname", required = false) String lastName,
                               @RequestParam(value = "fname", required = false) String firstName,
                               @RequestParam(value = "hotelName", required = false) String hotelName,
                               @RequestParam(value = "operaUser", required = false) String operaUser,
                               @RequestParam(value = "confNumber", required = false) String confNumber,
                               @RequestParam(value = "currency", required = false) String currency,
                               @RequestParam(value = "requestAmount", required = false) String requestAmount,
                               @RequestParam(value = "guestCountry", required = false) String guestCountry,
                               @RequestParam(value = "title", required = false) String title,
                               @RequestParam(value = "merchantID", required = false) String merchantID) {


        model.addAttribute("firstName" , firstName);
        model.addAttribute("lastName" , lastName);
        model.addAttribute("hotelName" , hotelName);
        model.addAttribute("operaUser" , operaUser);
        model.addAttribute("confNumber" , confNumber);
        model.addAttribute("currency" , currency);
        model.addAttribute("requestAmount" , requestAmount);
        model.addAttribute("guestCountry" , guestCountry);
        model.addAttribute("title" , title);
        model.addAttribute("merchantID" , merchantID);


        /*SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo("ivan.ivkosic@gmail.com");
        mailMessage.setFrom(configUtil.getProperty("fromEmail"));
        mailMessage.setSubject("--PAYMENT–");
        StringBuilder sb = new StringBuilder();
        sb.append("Hi I am Ivan");

        mailMessage.setText(sb.toString());
        javaMailSender.send(mailMessage);*/


        return "payment/index.html";
    }


}
