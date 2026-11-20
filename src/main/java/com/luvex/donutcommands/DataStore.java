package com.luvex.donutcore;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DataStore {
    private final DonutCore plugin;
    private final Map<String,YamlConfiguration> docs = new HashMap<>();
    private final Map<String,File> files = new HashMap<>();
    public DataStore(DonutCore plugin) { this.plugin=plugin; for (String n: List.of("balances","shards","market","orders","bounties","social","history","profiles")) load(n); }
    private void load(String name) {
        File f=new File(plugin.getDataFolder(),name+".yml");
        if (!f.exists()) try { plugin.getDataFolder().mkdirs(); f.createNewFile(); } catch(IOException ignored) {}
        files.put(name,f); docs.put(name,YamlConfiguration.loadConfiguration(f));
    }
    public YamlConfiguration doc(String n) { return docs.get(n); }
    public void save(String n) { try { docs.get(n).save(files.get(n)); } catch(IOException e) { plugin.getLogger().severe("Save "+n+": "+e.getMessage()); } }
    public double get(String doc,String path) { return docs.get(doc).getDouble(path,0); }
    public void set(String doc,String path,double v) { docs.get(doc).set(path,v); save(doc); }
    public boolean take(String doc,String path,double v) { double have=get(doc,path); if(v<=0 || have+1e-8<v)return false; set(doc,path,have-v); return true; }
    public void add(String doc,String path,double v) { if(v>0)set(doc,path,get(doc,path)+v); }
    public String key(UUID id) { return id.toString(); }
    public void history(UUID id,String line) {
        var d=doc("history"); String p=id.toString();
        List<String> list=d.getStringList(p); list.add(0,System.currentTimeMillis()+"|"+line);
        while(list.size()>50) list.remove(list.size()-1);
        d.set(p,list); save("history");
    }
    public void giveItem(org.bukkit.entity.Player p, ItemStack item) {
        if(item==null)return;
        Map<Integer,ItemStack> left=p.getInventory().addItem(item.clone());
        left.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x));
    }
}
