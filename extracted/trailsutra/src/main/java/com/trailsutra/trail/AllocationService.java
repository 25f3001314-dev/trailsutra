package com.trailsutra.trail;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AllocationService {
    public List<Allocation> fifo(long taintedBalance,long totalBalance,List<Long> outgoing){long remaining=Math.min(taintedBalance,totalBalance);List<Allocation> result=new ArrayList<>();for(long amount:outgoing){long tainted=Math.min(remaining,amount);result.add(new Allocation(amount,tainted));remaining-=tainted;}return result;}
    public List<Allocation> proportional(long taintedBalance,long totalBalance,List<Long> outgoing){if(totalBalance<=0)return outgoing.stream().map(a->new Allocation(a,0)).toList();List<Allocation> result=new ArrayList<>();long remaining=Math.min(taintedBalance,totalBalance);for(int i=0;i<outgoing.size();i++){long amount=outgoing.get(i);long tainted=i==outgoing.size()-1?Math.min(remaining,Math.round(amount*(double)taintedBalance/totalBalance)):Math.min(remaining,Math.round(amount*(double)taintedBalance/totalBalance));result.add(new Allocation(amount,tainted));remaining-=tainted;}return result;}
    public long lien(long taintedHeld,long currentBalance){return Math.min(Math.max(0,taintedHeld),Math.max(0,currentBalance));}
    public record Allocation(long amountPaise,long taintedPaise){}
}
