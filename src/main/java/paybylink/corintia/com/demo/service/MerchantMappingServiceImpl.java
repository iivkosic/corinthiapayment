package paybylink.corintia.com.demo.service;

import org.springframework.stereotype.Service;
import paybylink.corintia.com.demo.model.MerchantMapping;
import paybylink.corintia.com.demo.repository.MerchantMappingRepository;

@Service
public class MerchantMappingServiceImpl {

    private final MerchantMappingRepository merchantMappingRepository;


    public MerchantMappingServiceImpl(MerchantMappingRepository merchantMappingRepository) {
        this.merchantMappingRepository = merchantMappingRepository;
    }

    public MerchantMapping findByMerchantID(String merchantID){
        return merchantMappingRepository.findByMerchantID(merchantID);
    }
}
