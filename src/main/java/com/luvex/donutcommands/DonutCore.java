package com.luvex.donutcore;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.text.DecimalFormat;
import java.util.*;

public final class DonutCore extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private PriceManager prices;
    private DataStore data;
    private final DecimalFormat fmt=new DecimalFormat("#,##0.##");
    private final Map<UUID,Inventory> sellMenus=new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig(); prices=new PriceManager(this); data=new DataStore(this);
        getServer().getPluginManager().registerEvents(this,this);
        for(String c:getDescription().getCommands().keySet()) {
            PluginCommand pc=getCommand(c); if(pc!=null){pc.setExecutor(this);pc.setTabCompleter(this);}
        }
        getLogger().info("DonutCommands 2.8.9 enabled.");
    }
    private String col(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}
    private String pre(){return col(getConfig().getString("prefix","&6DonutCore &8» "));}
    private String money(double n){return getConfig().getString("currency-symbol","$")+fmt.format(n);}
    private void tell(CommandSender s,String m){s.sendMessage(pre()+col(m));}
    private boolean player(CommandSender s){if(s instanceof Player)return true;tell(s,"&cPlayers only.");return false;}
    private String path(Player p){return p.getUniqueId().toString();}
    private double bal(UUID id){return data.get("balances",id.toString());}
    private void credit(UUID id,double n){data.add("balances",id.toString(),n);}
    private boolean debit(UUID id,double n){return data.take("balances",id.toString(),n);}
    private double shards(UUID id){return data.get("shards",id.toString());}
    private boolean admin(CommandSender s){if(s.hasPermission("donutcore.admin"))return true;tell(s,"&cNo permission.");return false;}
    private double amount(String raw){return PriceManager.parse(raw);}
    private String itemName(ItemStack i){return i.getType().name();}
    private void openSell(Player p){Inventory inv=Bukkit.createInventory(null,54,col(getConfig().getString("sell-gui-title","&8Sell Items")));sellMenus.put(p.getUniqueId(),inv);p.openInventory(inv);tell(p,"&7Put items in the menu. Close it to sell priced items.");}

    @Override public synchronized boolean onCommand(CommandSender s,Command cmd,String label,String[] a) {
        String c=cmd.getName().toLowerCase(Locale.ROOT);
        switch(c){
            case "bal" -> { if(a.length==0&&s instanceof Player p)tell(s,"&aBalance: &f"+money(bal(p.getUniqueId())));
                else if(a.length==1){OfflinePlayer op=Bukkit.getOfflinePlayer(a[0]);tell(s,"&e"+a[0]+" balance: &f"+money(bal(op.getUniqueId())));}else tell(s,"&cUsage: /bal [player]"); }
            case "pay" -> {if(!player(s))return true;Player p=(Player)s;if(a.length!=2){tell(s,"&c/pay <player> <amount>");return true;}
                Player t=Bukkit.getPlayerExact(a[0]);double n=amount(a[1]);double tax=n*getConfig().getDouble("transaction-tax-percent",2)/100;
                if(t==null||t.equals(p)||n<=0){tell(s,"&cInvalid player or amount.");return true;}
                if(!debit(p.getUniqueId(),n+tax)){tell(s,"&cInsufficient funds (including tax "+money(tax)+").");return true;}
                credit(t.getUniqueId(),n);tell(p,"&aPaid "+money(n)+" to "+t.getName()+" (tax "+money(tax)+").");tell(t,"&aReceived "+money(n)+" from "+p.getName()+".");}
            case "baltop" -> {tell(s,"&6&lBalance Top");top(s,"balances");}
            case "shards" -> {if(a.length==0&&s instanceof Player p)tell(s,"&dShards: &f"+fmt.format(shards(p.getUniqueId())));
                else if(a.length==1){OfflinePlayer op=Bukkit.getOfflinePlayer(a[0]);tell(s,"&d"+a[0]+" shards: &f"+fmt.format(shards(op.getUniqueId())));}else tell(s,"&c/shards [player]");}
            case "shardpay" -> {if(!player(s))return true;Player p=(Player)s;if(a.length!=2){tell(s,"&c/shardpay <player> <amount>");return true;}Player t=Bukkit.getPlayerExact(a[0]);double n=amount(a[1]);if(t==null||n<=0||t.equals(p)||!data.take("shards",path(p),n)){tell(s,"&cInvalid target or insufficient shards.");return true;}data.add("shards",path(t),n);tell(p,"&aSent "+fmt.format(n)+" shards.");tell(t,"&aReceived "+fmt.format(n)+" shards.");}
            case "sell" -> {if(player(s))openSell((Player)s);}
            case "sellhand" -> {if(!player(s))return true;Player p=(Player)s;ItemStack it=p.getInventory().getItemInMainHand();int max=a.length>0?(int)amount(a[0]):it.getAmount();sellHand(p,max);}
            case "sellall" -> {if(!player(s))return true;Player p=(Player)s;int count=0;double total=0;for(int i=0;i<p.getInventory().getStorageContents().length;i++){ItemStack it=p.getInventory().getItem(i);if(it==null||it.getType().isAir())continue;double unit=prices.get(it.getType());if(unit>0){count+=it.getAmount();total+=unit*it.getAmount();data.history(p.getUniqueId(),it.getAmount()+"x "+it.getType()+" = "+money(unit*it.getAmount()));p.getInventory().setItem(i,null);}}if(count>0){credit(p.getUniqueId(),total);tell(p,"&aSold "+count+" items for "+money(total)+".");}else tell(p,"&cNo priced items found.");}
            case "sellhistory" -> {if(!player(s))return true;List<String> h=data.doc("history").getStringList(path((Player)s));tell(s,"&6Recent sales:");if(h.isEmpty())tell(s,"&7No sales yet.");else h.stream().limit(10).forEach(x->tell(s,"&7"+x.substring(x.indexOf('|')+1)));}
            case "worth","prices" -> {if(a.length==0&&s instanceof Player p){ItemStack hand=p.getInventory().getItemInMainHand();if(!hand.getType().isAir())tell(s,"&e"+hand.getType()+" worth: &a"+money(prices.get(hand.getType())));else listPrices(s);}
                else if(a.length==1){Material m=Material.matchMaterial(a[0]);if(m==null)tell(s,"&cUnknown material.");else tell(s,"&e"+m+" worth: &a"+money(prices.get(m)));}else listPrices(s);}
            case "price" -> {if(!admin(s))return true;if(a.length==1&&a[0].equalsIgnoreCase("reload")){prices.reload();tell(s,"&aPrices reloaded.");}
                else if(a.length==3&&a[0].equalsIgnoreCase("set")){Material m=Material.matchMaterial(a[1]);if(m==null){tell(s,"&cUnknown material.");return true;}prices.set(m,a[2]);tell(s,"&aSet "+m+" to "+a[2]);}
                else tell(s,"&c/price reload | /price set <material> <amount>");}
            case "ah","auctionhouse" -> auction(s,a);
            case "orders","order" -> orders(s,a);
            case "bounty" -> bounty(s,a);
            case "leaderboard" -> {tell(s,"&6Leaderboards: &f/baltop, /bounty");}
            case "stats" -> {OfflinePlayer op=a.length>0?Bukkit.getOfflinePlayer(a[0]):(s instanceof Player p?p:null);if(op==null){tell(s,"&c/stats [player]");return true;}tell(s,"&eStats for "+op.getName()+": &7Balance "+money(bal(op.getUniqueId()))+" | Shards "+fmt.format(shards(op.getUniqueId())));}
            case "ping" -> {Player t=a.length>0?Bukkit.getPlayerExact(a[0]):(s instanceof Player p?p:null);if(t==null)tell(s,"&cPlayer not online.");else tell(s,"&a"+t.getName()+" ping: "+(t.getPing())+"ms");}
            case "findplayer" -> {if(a.length!=1)tell(s,"&c/findplayer <player>");else{Player t=Bukkit.getPlayerExact(a[0]);tell(s,t==null?"&cPlayer is offline.":"&a"+t.getName()+" is online in "+t.getWorld().getEnvironment().name()+".");}}
            case "block","ignore" -> socialBlock(s,a,true);
            case "unblock" -> socialBlock(s,a,false);
            case "friend" -> friend(s,a);
            case "settings" -> {if(player(s))tell(s,"&eSettings are stored per player; social blocking is available via /block and /unblock.");}
            case "help" -> {tell(s,"&6DonutCommands 2.8.9 &7- /bal /pay /baltop /sell /sellall /sellhand /sellhistory /worth /ah /orders /bounty /shards /shardpay /stats /ping");}
            case "rules" -> {tell(s,"&6Server Rules:");for(String r:getConfig().getStringList("rules"))tell(s,r);}
            case "discord" -> tell(s,"&eDiscord: &f"+getConfig().getString("discord-url"));
            case "ranks" -> tell(s,"&e"+getConfig().getString("rank-info"));
            case "meta" -> tell(s,"&eCurrent farming meta: &f"+getConfig().getString("farming-meta"));
            case "whereami" -> tell(s,"&eReport reference: &f"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT));
            case "link" -> {if(!player(s))return true;Player p=(Player)s;String code=UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase(Locale.ROOT);data.doc("profiles").set(path(p)+".link-code",code);data.save("profiles");tell(p,"&eYour one-time link code: &f"+code);}
            case "api" -> {if(!player(s))return true;Player p=(Player)s;if(a.length>0&&a[0].equalsIgnoreCase("revoke")){data.doc("profiles").set(path(p)+".api-key",null);data.save("profiles");tell(p,"&aLocal API token revoked.");}else{String key=data.doc("profiles").getString(path(p)+".api-key");if(key==null){key=UUID.randomUUID().toString()+UUID.randomUUID().toString().replace("-","");data.doc("profiles").set(path(p)+".api-key",key);data.save("profiles");}tell(p,"&eLocal API key: &f"+key);}}
            case "donutcore" -> {if(!admin(s))return true;if(a.length>0&&a[0].equalsIgnoreCase("reload")){reloadConfig();prices.reload();tell(s,"&aConfig reloaded.");}else tell(s,"&eDonutCommands 2.8.9 | /donutcore reload");}
            case "dcadmin" -> adminCommand(s,a);
            default -> tell(s,"&cUnknown command.");
        }
        return true;
    }

    private void sellHand(Player p,int requested){ItemStack it=p.getInventory().getItemInMainHand();if(it==null||it.getType().isAir()){tell(p,"&cHold an item.");return;}double unit=prices.get(it.getType());if(unit<=0){tell(p,"&cNo sell price for this item.");return;}int n=Math.min(it.getAmount(),Math.max(1,requested));ItemStack sold=it.clone();sold.setAmount(n);if(it.getAmount()==n)p.getInventory().setItemInMainHand(null);else it.setAmount(it.getAmount()-n);double total=unit*n;credit(p.getUniqueId(),total);data.history(p.getUniqueId(),n+"x "+it.getType()+" = "+money(total));tell(p,"&aSold "+n+"x "+it.getType()+" for "+money(total));}
    @EventHandler public synchronized void closeSell(InventoryCloseEvent e){if(!(e.getPlayer() instanceof Player p))return;Inventory inv=sellMenus.remove(p.getUniqueId());if(inv==null||inv!=e.getInventory())return;double total=0;int count=0;for(ItemStack it:inv.getContents()){if(it==null||it.getType().isAir())continue;double unit=prices.get(it.getType());if(unit>0){total+=unit*it.getAmount();count+=it.getAmount();data.history(p.getUniqueId(),it.getAmount()+"x "+it.getType()+" = "+money(unit*it.getAmount()));}else data.giveItem(p,it);}if(count>0){credit(p.getUniqueId(),total);tell(p,"&aSold "+count+" items for "+money(total));}}

    private void auction(CommandSender s,String[] a){
        if(!(s instanceof Player p)){tell(s,"&cPlayers only.");return;}
        var d=data.doc("market");
        if(a.length==0){tell(p,"&6Auction House &7- /ah sell <price> | /ah search <material> | /ah my | /ah claims");int n=0;for(String id:d.getKeys(false)){if(n++>=10)break;String owner=d.getString(id+".owner");Material m=Material.matchMaterial(d.getString(id+".material","STONE"));tell(p,"&e#"+id.substring(0,Math.min(6,id.length()))+" &f"+m+" &7x"+d.getInt(id+".amount")+" &a"+money(d.getDouble(id+".price"))+" &7by "+owner);}return;}
        if(a[0].equalsIgnoreCase("sell")){if(a.length!=2){tell(p,"&c/ah sell <price>");return;}ItemStack hand=p.getInventory().getItemInMainHand();double price=amount(a[1]);if(hand.getType().isAir()||price<=0){tell(p,"&cHold an item and enter a valid price.");return;}String id=UUID.randomUUID().toString();d.set(id+".owner",p.getName());d.set(id+".owner-uuid",path(p));d.set(id+".material",hand.getType().name());d.set(id+".amount",hand.getAmount());d.set(id+".item",hand);d.set(id+".price",price);data.save("market");p.getInventory().setItemInMainHand(null);tell(p,"&aListed "+hand.getAmount()+"x "+hand.getType()+" for "+money(price));}
        else if(a[0].equalsIgnoreCase("my")){for(String id:d.getKeys(false))if(d.getString(id+".owner-uuid","").equals(path(p)))tell(p,"&e"+id.substring(0,8)+" &f"+d.getString(id+".material")+" &a"+money(d.getDouble(id+".price")));}
        else if(a[0].equalsIgnoreCase("search")&&a.length>1){String q=String.join(" ",Arrays.copyOfRange(a,1,a.length)).toUpperCase(Locale.ROOT);for(String id:d.getKeys(false))if(d.getString(id+".material","").contains(q))tell(p,"&e"+id.substring(0,8)+" &f"+d.getString(id+".material")+" &7x"+d.getInt(id+".amount")+" &a"+money(d.getDouble(id+".price"))+" &7/sell with /ah buy "+id.substring(0,8));}
        else if(a[0].equalsIgnoreCase("buy")&&a.length==2){String id=findId(d,a[1]);if(id==null){tell(p,"&cListing not found.");return;}if(d.getString(id+".owner-uuid","").equals(path(p))){tell(p,"&cYou cannot buy your own listing.");return;}double price=d.getDouble(id+".price"),tax=price*getConfig().getDouble("transaction-tax-percent",2)/100;if(!debit(p.getUniqueId(),price+tax)){tell(p,"&cInsufficient balance including tax.");return;}UUID owner=UUID.fromString(d.getString(id+".owner-uuid"));credit(owner,price);ItemStack it=d.getItemStack(id+".item");data.giveItem(p,it);d.set(id,null);data.save("market");tell(p,"&aPurchased for "+money(price)+" (tax "+money(tax)+").");}
        else if(a[0].equalsIgnoreCase("cancel")&&a.length==2){String id=findId(d,a[1]);if(id==null||!d.getString(id+".owner-uuid","").equals(path(p))){tell(p,"&cListing not found or not yours.");return;}data.giveItem(p,d.getItemStack(id+".item"));d.set(id,null);data.save("market");tell(p,"&aListing cancelled.");}
        else tell(p,"&c/ah [search <item>|sell <price>|buy <id>|cancel <id>|my]");
    }
    private String findId(YamlConfiguration d,String shortId){for(String id:d.getKeys(false))if(id.startsWith(shortId))return id;return null;}

    private void orders(CommandSender s,String[] a){
        if(!(s instanceof Player p)){tell(s,"&cPlayers only.");return;}var d=data.doc("orders");
        if(a.length==0){tell(p,"&6Orders &7- /orders create <material> <amount> <unit-price> | /orders fill <id> | /orders my | /orders cancel <id>");for(String id:d.getKeys(false))tell(p,"&e"+id.substring(0,8)+" &f"+d.getString(id+".material")+" &7x"+d.getInt(id+".remaining")+" &a"+money(d.getDouble(id+".unit-price"))+"/item");return;}
        if(a[0].equalsIgnoreCase("create")&&a.length==4){Material m=Material.matchMaterial(a[1]);int qty;try{qty=Integer.parseInt(a[2]);}catch(Exception ex){qty=0;}double unit=amount(a[3]),total=qty*unit,tax=total*getConfig().getDouble("transaction-tax-percent",2)/100;if(m==null||!m.isItem()||qty<=0||unit<=0||!debit(p.getUniqueId(),total+tax)){tell(p,"&cInvalid order or insufficient balance (tax included).");return;}String id=UUID.randomUUID().toString();d.set(id+".owner",path(p));d.set(id+".owner-name",p.getName());d.set(id+".material",m.name());d.set(id+".remaining",qty);d.set(id+".unit-price",unit);d.set(id+".escrow",total);data.save("orders");tell(p,"&aOrder created: "+qty+"x "+m+" at "+money(unit)+" each.");}
        else if(a[0].equalsIgnoreCase("fill")&&a.length==2){String id=findId(d,a[1]);if(id==null){tell(p,"&cOrder not found.");return;}if(d.getString(id+".owner","").equals(path(p))){tell(p,"&cCannot fill your own order.");return;}Material m=Material.matchMaterial(d.getString(id+".material"));int available=0;for(ItemStack it:p.getInventory().getStorageContents())if(it!=null&&it.getType()==m)available+=it.getAmount();int qty=Math.min(available,d.getInt(id+".remaining"));if(qty<=0){tell(p,"&cYou have none of the requested item.");return;}int left=qty;for(int i=0;i<p.getInventory().getStorageContents().length&&left>0;i++){ItemStack it=p.getInventory().getItem(i);if(it==null||it.getType()!=m)continue;int take=Math.min(left,it.getAmount());it.setAmount(it.getAmount()-take);if(it.getAmount()<=0)p.getInventory().setItem(i,null);left-=take;}double pay=qty*d.getDouble(id+".unit-price");credit(p.getUniqueId(),pay);UUID buyer=UUID.fromString(d.getString(id+".owner"));credit(buyer,0);d.set(id+".remaining",d.getInt(id+".remaining")-qty);d.set(id+".escrow",Math.max(0,d.getDouble(id+".escrow")-pay));if(d.getInt(id+".remaining")<=0)d.set(id,null);data.save("orders");tell(p,"&aFilled "+qty+" items and received "+money(pay)+".");}
        else if(a[0].equalsIgnoreCase("my")){for(String id:d.getKeys(false))if(d.getString(id+".owner","").equals(path(p)))tell(p,"&e"+id.substring(0,8)+" &f"+d.getString(id+".material")+" remaining "+d.getInt(id+".remaining"));}
        else if(a[0].equalsIgnoreCase("cancel")&&a.length==2){String id=findId(d,a[1]);if(id==null||!d.getString(id+".owner","").equals(path(p))){tell(p,"&cOrder not found or not yours.");return;}double refund=d.getDouble(id+".escrow");credit(p.getUniqueId(),refund);d.set(id,null);data.save("orders");tell(p,"&aOrder cancelled; refunded "+money(refund)+".");}
        else tell(p,"&c/orders [create|fill|my|cancel]");
    }

    private void bounty(CommandSender s,String[] a){
        if(!(s instanceof Player p)){tell(s,"&cPlayers only.");return;}var d=data.doc("bounties");
        if(a.length==0){tell(p,"&6Bounty board");for(String id:d.getKeys(false))tell(p,"&e"+Bukkit.getOfflinePlayer(UUID.fromString(id)).getName()+" &7- &c"+money(d.getDouble(id)));return;}
        if(a[0].equalsIgnoreCase("add")&&a.length==3){Player target=Bukkit.getPlayerExact(a[1]);double n=amount(a[2]);if(target==null||target.equals(p)||n<=0||!debit(p.getUniqueId(),n)){tell(p,"&cInvalid target or insufficient balance.");return;}d.set(target.getUniqueId().toString(),d.getDouble(target.getUniqueId().toString())+n);data.save("bounties");tell(p,"&aAdded "+money(n)+" bounty to "+target.getName());}
        else{OfflinePlayer target=Bukkit.getOfflinePlayer(a[0]);tell(p,"&eBounty for "+a[0]+": &c"+money(d.getDouble(target.getUniqueId().toString())));}
    }
    private void top(CommandSender s,String doc){var d=data.doc(doc);List<Map.Entry<String,Double>> list=new ArrayList<>();for(String k:d.getKeys(false))list.add(Map.entry(k,d.getDouble(k)));list.sort((x,y)->Double.compare(y.getValue(),x.getValue()));int i=1;for(var e:list.stream().limit(10).toList()){String name;try{name=Bukkit.getOfflinePlayer(UUID.fromString(e.getKey())).getName();}catch(Exception ex){name=e.getKey();}tell(s,"&e#"+i+++" &f"+name+" &a"+money(e.getValue()));}}
    private void listPrices(CommandSender s){tell(s,"&6Price catalog (configured items):");int n=0;for(Material m:Material.values())if(m.isItem()&&prices.get(m)>0){tell(s,"&e"+m.name()+" &7- &a"+money(prices.get(m)));if(++n>=30)break;}}
    private void socialBlock(CommandSender s,String[] a,boolean add){if(!(s instanceof Player p)){tell(s,"&cPlayers only.");return;}if(a.length!=1){tell(s,add?"&c/block <player>":"&c/unblock <player>");return;}OfflinePlayer t=Bukkit.getOfflinePlayer(a[0]);String key=path(p)+".blocked";List<String> list=data.doc("social").getStringList(key);if(add){if(!list.contains(t.getUniqueId().toString()))list.add(t.getUniqueId().toString());}else list.remove(t.getUniqueId().toString());data.doc("social").set(key,list);data.save("social");tell(p,add?"&aPlayer blocked.":"&aPlayer unblocked.");}
    private void friend(CommandSender s,String[] a){if(!(s instanceof Player p)){tell(s,"&cPlayers only.");return;}if(a.length==0||a[0].equalsIgnoreCase("list")){List<String> f=data.doc("social").getStringList(path(p)+".friends");tell(p,"&eFriends: &f"+f.size());for(String id:f){String n=Bukkit.getOfflinePlayer(UUID.fromString(id)).getName();tell(p,"&7- "+n);}return;}if(a.length==2&&a[0].equalsIgnoreCase("add")){OfflinePlayer t=Bukkit.getOfflinePlayer(a[1]);List<String> f=data.doc("social").getStringList(path(p)+".friends");if(!f.contains(t.getUniqueId().toString()))f.add(t.getUniqueId().toString());data.doc("social").set(path(p)+".friends",f);data.save("social");tell(p,"&aAdded friend (local friend list).");}else if(a.length==2&&a[0].equalsIgnoreCase("remove")){OfflinePlayer t=Bukkit.getOfflinePlayer(a[1]);List<String> f=data.doc("social").getStringList(path(p)+".friends");f.remove(t.getUniqueId().toString());data.doc("social").set(path(p)+".friends",f);data.save("social");tell(p,"&aRemoved friend.");}else tell(p,"&c/friend [list|add <player>|remove <player>]");}
    private void adminCommand(CommandSender s,String[] a){if(!admin(s))return;if(a.length==3){OfflinePlayer t=Bukkit.getOfflinePlayer(a[1]);double n=amount(a[2]);switch(a[0].toLowerCase(Locale.ROOT)){case "setbalance"->data.set("balances",t.getUniqueId().toString(),n);case "addbalance"->credit(t.getUniqueId(),n);case "removebalance"->data.take("balances",t.getUniqueId().toString(),n);case "setshards"->data.set("shards",t.getUniqueId().toString(),n);default->{tell(s,"&c/dcadmin <setbalance|addbalance|removebalance|setshards> <player> <amount>");return;}}tell(s,"&aUpdated "+a[1]+".");}else tell(s,"&c/dcadmin <setbalance|addbalance|removebalance|setshards> <player> <amount>");}

    @Override public List<String> onTabComplete(CommandSender s,Command c,String alias,String[] a){String n=c.getName().toLowerCase(Locale.ROOT);if(a.length==1){if(n.equals("price"))return List.of("reload","set");if(n.equals("ah")||n.equals("auctionhouse"))return List.of("sell","search","my","buy","cancel");if(n.equals("orders")||n.equals("order"))return List.of("create","fill","my","cancel");if(n.equals("bounty"))return List.of("add");if(n.equals("friend"))return List.of("list","add","remove");if(n.equals("donutcore"))return List.of("reload");}if(n.equals("worth")&&a.length==1)return Arrays.stream(Material.values()).filter(Material::isItem).map(m->m.name().toLowerCase(Locale.ROOT)).filter(x->x.startsWith(a[0].toLowerCase(Locale.ROOT))).limit(40).toList();return Collections.emptyList();}
}
