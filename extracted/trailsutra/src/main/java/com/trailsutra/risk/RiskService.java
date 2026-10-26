package com.trailsutra.risk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RiskService {
    private final double intercept; private final Map<String,Double> coefficients; private final double[] calibration;
    public RiskService(@Value("${trailsutra.risk.intercept}") double intercept,
                       @Value("${trailsutra.risk.coefficients.pass-through}") double passThrough,
                       @Value("${trailsutra.risk.coefficients.fan-out}") double fanOut,
                       @Value("${trailsutra.risk.coefficients.shared-endpoints}") double sharedEndpoints,
                       @Value("${trailsutra.risk.coefficients.burst}") double burst,
                       @Value("${trailsutra.risk.coefficients.cycle}") double cycle,
                       @Value("${trailsutra.risk.coefficients.retained-balance}") double retainedBalance,
                       @Value("${trailsutra.risk.coefficients.account-age}") double accountAge,
                       @Value("${trailsutra.risk.coefficients.unique-customers}") double uniqueCustomers,
                       @Value("${trailsutra.risk.calibration}") double[] calibration){
        this.intercept=intercept;this.calibration=calibration.clone();Map<String,Double> c=new LinkedHashMap<>();c.put("pass-through",passThrough);c.put("fan-out",fanOut);c.put("shared-endpoints",sharedEndpoints);c.put("burst",burst);c.put("cycle",cycle);c.put("retained-balance",retainedBalance);c.put("account-age",accountAge);c.put("unique-customers",uniqueCustomers);this.coefficients=Map.copyOf(c);
    }
    public Result score(Map<String,Double> features){double logOdds=intercept;Map<String,Double> contributions=new LinkedHashMap<>();for(var entry:coefficients.entrySet()){double contribution=entry.getValue()*features.getOrDefault(entry.getKey(),0.0);contributions.put(entry.getKey(),contribution);logOdds+=contribution;}double raw=1.0/(1.0+Math.exp(-logOdds));int index=Math.min(calibration.length-1,(int)Math.floor(raw*calibration.length));double calibrated=calibration.length==0?raw:calibration[index];return new Result(raw,calibrated,logOdds,Map.copyOf(contributions));}
    public record Result(double rawScore,double calibratedScore,double logOdds,Map<String,Double> contributions){}
}
