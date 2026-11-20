package com.luvex.donutcore;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class PriceManager {
    private final DonutCore plugin;
    private File file;
    private YamlConfiguration yaml;

    public PriceManager(DonutCore plugin) { this.plugin = plugin; reload(); }
    public void reload() {
        file = new File(plugin.getDataFolder(), "price.yml");
        if (!file.exists()) plugin.saveResource("price.yml", false);
        yaml = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (Material m : Material.values()) {
            if (m.isItem() && !yaml.contains("prices." + m.name())) {
                yaml.set("prices." + m.name(), 0);
                changed = true;
            }
        }
        if (changed) save();
    }
    public double get(Material m) {
        if (m == null || !m.isItem()) return 0;
        return parse(yaml.getString("prices." + m.name(), String.valueOf(yaml.get("prices." + m.name(), 0))));
    }
    public void set(Material m, String value) { yaml.set("prices." + m.name(), value); save(); }
    public void save() { try { yaml.save(file); } catch (IOException e) { plugin.getLogger().severe(e.getMessage()); } }
    public static double parse(String raw) {
        if (raw == null) return 0;
        String s=raw.trim().toLowerCase(Locale.ROOT).replace(",", "");
        double mult=1;
        if (s.endsWith("k")) { mult=1e3; s=s.substring(0,s.length()-1); }
        else if (s.endsWith("m")) { mult=1e6; s=s.substring(0,s.length()-1); }
        else if (s.endsWith("b")) { mult=1e9; s=s.substring(0,s.length()-1); }
        else if (s.endsWith("t")) { mult=1e12; s=s.substring(0,s.length()-1); }
        try { return Math.max(0, Double.parseDouble(s)*mult); } catch (Exception e) { return 0; }
    }
}
