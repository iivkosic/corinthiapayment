package paybylink.corintia.com.demo.controller;

import com.adyen.Client;
import com.adyen.enums.Environment;
import com.adyen.model.Amount;
import com.adyen.model.checkout.CreatePaymentLinkRequest;
import com.adyen.model.checkout.PaymentLinkResource;
import com.adyen.service.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;

import com.adyen.service.PaymentLinks;
import paybylink.corintia.com.demo.model.PaymentNotification;
import paybylink.corintia.com.demo.service.PaymentNotificationServiceImpl;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;


@RestController
public class PaymentRest {

    private final String serverUrl = "https://589ca91f8810687e-corinthia-checkout-live.adyenpayments.com";
    private final String version = "v69";
    private final String APIKey = "AQEkhmfuXNWTK0Qc+iSTnXYxqfCTQYrRE9yW+5BnpSNd0yUi5j6OEMFdWw2+5HzctViMSCJMYAc=-5rE6sYEZgJAP41k6OUdsYWVuJLUC9L6fC2bXf+NruS8=-xYWUG5&Gfa}5&&uc";
    private final String merchantAccount = "Corinthia";


    public PaymentRest(ObjectMapper objectMapper, PaymentNotificationServiceImpl paymentNotificationService) {
        this.objectMapper = objectMapper;
        this.paymentNotificationService = paymentNotificationService;
    }

    private final ObjectMapper objectMapper;

    private final PaymentNotificationServiceImpl paymentNotificationService;

    @PostMapping(value = "/generatelink")
    public ObjectNode generatePaymentLink(HttpSession session, @RequestParam(value = "lname", required = false) String lastName,
                                          @RequestParam(value = "fname", required = false) String firstName,
                                          @RequestParam(value = "hotelName", required = false) String hotelName,
                                          @RequestParam(value = "operaUser", required = false) String operaUser,
                                          @RequestParam(value = "confNumber", required = false) String confNumber,
                                          @RequestParam(value = "currency", required = false) String currency,
                                          @RequestParam(value = "requestAmount", required = false) Double requestAmount,
                                          @RequestParam(value = "guestCountry", required = false) String guestCountry,
                                          @RequestParam(value = "title", required = false) String title,
                                          @RequestParam(value = "merchantID", required = false) String merchantID) {

        Map<String, String> countryLocales = new HashMap<>();
        requestAmount = Math.round(requestAmount*100.0)/100.0;
        requestAmount = requestAmount * 100;

        countryLocales.put("HR", "hr-HR");
        countryLocales.put("CZ", "cs-CZ");
        countryLocales.put("DK", "da-DK");
        countryLocales.put("NL", "nl-NL");
        countryLocales.put("US", "en-US");
        countryLocales.put("HR", "hr-HR");
        countryLocales.put("HR", "hr-HR");
        countryLocales.put("HR", "hr-HR");
        countryLocales.put("HR", "hr-HR");
        countryLocales.put("HR", "hr-HR");
        countryLocales.put("HR", "hr-HR");

        ObjectNode objectNode = objectMapper.createObjectNode();
        String generatedLink = "";
        Locale locale = LocaleContextHolder.getLocale();

        List<CreatePaymentLinkRequest.RequiredShopperFieldsEnum> requiredShopperFields = new ArrayList<>();
        requiredShopperFields.add(CreatePaymentLinkRequest.RequiredShopperFieldsEnum.fromValue("shopperEmail"));

        String timeStamp = new SimpleDateFormat("yyMMddHHmmss").format(new java.util.Date());


        String xApiKey = APIKey;
        Client client = new Client(xApiKey, Environment.LIVE,"589ca91f8810687e-corinthia");
        PaymentLinks paymentLinks = new PaymentLinks(client);
        CreatePaymentLinkRequest createPaymentLinkRequest = new CreatePaymentLinkRequest();
        Amount amount = new Amount();
        amount.setCurrency(currency);
        amount.setValue(requestAmount.longValue());
        createPaymentLinkRequest.setAmount(amount);
        createPaymentLinkRequest.setReference(timeStamp + hotelName + confNumber);
        createPaymentLinkRequest.setShopperReference(timeStamp + hotelName + confNumber);
        //createPaymentLinkRequest.setDescription("Blue Bag - ModelM671");
        createPaymentLinkRequest.setCountryCode(guestCountry);
        createPaymentLinkRequest.setMerchantAccount(merchantID);
        createPaymentLinkRequest.setShopperLocale(locale.toString());
        createPaymentLinkRequest.setRequiredShopperFields(requiredShopperFields);

        PaymentLinkResource response = null;
        try {
            response = paymentLinks.create(createPaymentLinkRequest);
        } catch (ApiException e) {
            objectNode.put("message", e.getError().getMessage());
            return objectNode;
        } catch (IOException e) {
            objectNode.put("message", "ERROR");
            e.printStackTrace();
        }


        generatedLink = response.getUrl();

        PaymentNotification paymentNotification = new PaymentNotification();
        paymentNotification.setFirst(firstName);
        paymentNotification.setLast(lastName);
        paymentNotification.setMerchantReference(timeStamp + hotelName + confNumber);
        paymentNotification.setTitle(title);
        paymentNotification.setPblGenerated(true);
        paymentNotification.setAmount(requestAmount.longValue());
        paymentNotificationService.savePaymentNotification(paymentNotification);

        objectNode.put("link", generatedLink);
        return objectNode;
    }

}
