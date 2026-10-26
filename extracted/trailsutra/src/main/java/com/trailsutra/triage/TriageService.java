package com.trailsutra.triage;

import com.trailsutra.trail.AllocationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TriageService {
    private final double relay,watch,monitor; private final AllocationService allocation;
    public TriageService(@Value("${trailsutra.trail.relay-threshold}") double relay,@Value("${trailsutra.trail.watch-threshold}") double watch,@Value("${trailsutra.trail.monitor-threshold}") double monitor,AllocationService allocation){this.relay=relay;this.watch=watch;this.monitor=monitor;this.allocation=allocation;}
    public Decision decide(double score,boolean structuralEvidence,boolean oldAccount,boolean manyCustomers,boolean forwardedQuickly,long taintedHeld,long balance){if(score>=relay&&structuralEvidence)return new Decision("Relay","Priority review",allocation.lien(taintedHeld,balance));if(score>=watch)return new Decision("Aggregator candidate","Watchlist review",allocation.lien(taintedHeld,balance));if(score>=monitor)return new Decision("Suspected lead","Monitor review",allocation.lien(taintedHeld,balance));if(oldAccount&&manyCustomers&&!forwardedQuickly)return new Decision("Other account","Review tainted amount",allocation.lien(taintedHeld,balance));return new Decision("Suspected lead","Manual review",allocation.lien(taintedHeld,balance));}
    public record Decision(String role,String recommendation,long lienPaise){}
}
