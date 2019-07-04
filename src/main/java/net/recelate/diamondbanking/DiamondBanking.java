package net.recelate.diamondbanking;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;


public final class DiamondBanking extends JavaPlugin
{
    private static final Logger log = Logger.getLogger("Minecraft");
    private static Economy econ = null;
    private static FileConfiguration config = null;

    @Override
    public void onEnable()
    {
        if( !setupEconomy() )
        {
            log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if( !loadConfig() )
        {
            log.severe(String.format("[%s] - Disabled, no configuration found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        config.addDefault("DiamondWorth", 300);
    }

    @Override
    public void onDisable()
    {
        log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
        if( config != null )
        {
            config.options().copyDefaults(true);
            saveConfig();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if( !(sender instanceof Player) )
        {
            log.info("Only players are supported for this action.");
            return false;
        }

        Player plr = (Player)sender;

        if( cmd.getLabel().equals("deposit") )
        {
            if( plr.getInventory().getItemInMainHand().getType() != Material.DIAMOND )
            {
                plr.sendMessage("You must be holding Diamonds to use this command!");
                return false;
            }

            int amount = plr.getInventory().getItemInMainHand().getAmount();
            EconomyResponse r = econ.depositPlayer(plr, amount * config.getInt("DiamondWorth"));
            if( r.transactionSuccess() )
            {
                plr.getInventory().getItemInMainHand().setAmount(0);
                plr.sendMessage(String.format("You have deposited %d Diamonds, for %s. Your balance is now %s", amount, econ.format(r.amount), econ.format(r.balance)));
            }
            else
                plr.sendMessage(String.format("An error occured: %s", r.errorMessage));

            return true;
        }

        else if( cmd.getLabel().equals("withdraw") )
        {
            if( args.length == 0 || Integer.parseInt(args[0]) == 0 )
            {
                plr.sendMessage("You must supply the amount of Diamonds you wish to withdraw.");
                return true;
            }

            int amount = Integer.parseInt(args[0]);
            if( econ.getBalance(plr) < amount * config.getInt("DiamondWorth") )
            {
                plr.sendMessage("You do not have enough money to do that!");
                return true;
            }

            List<Integer> amounts = new ArrayList<Integer>();

            for( int x = 0; x < amount / 64; x++ )
                amounts.add(64);
            amounts.add(amount % 64);

            EconomyResponse r = econ.withdrawPlayer(plr, amount * config.getInt("DiamondWorth"));
            if( r.transactionSuccess() )
            {
                for(Integer amt : amounts )
                {
                    ItemStack diamonds = new ItemStack(Material.DIAMOND, amt);
                    plr.getInventory().addItem(diamonds);
                }

                plr.sendMessage(String.format("You have withdrew %d Diamonds, for %s. Your balance is now %s", amount, econ.format(r.amount), econ.format(r.balance)));
            }
            else
                plr.sendMessage(String.format("An error occured: %s", r.errorMessage));

            return true;
        }

        return false;
    }

    private boolean setupEconomy()
    {
        if( getServer().getPluginManager().getPlugin("Vault") == null )
            return false;

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if( rsp == null )
            return false;

        econ = rsp.getProvider();
        return econ != null;
    }

    private boolean loadConfig()
    {
        saveDefaultConfig();

        config = getConfig();
        return config != null;
    }
}
