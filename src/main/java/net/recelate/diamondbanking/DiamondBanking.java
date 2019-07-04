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

        if( cmd.getLabel().toLowerCase().equals("deposit") )
            return handleDeposit(sender, cmd, label, args);

        else if( cmd.getLabel().toLowerCase().equals("withdraw") )
            return handleWithdraw(sender, cmd, label, args);

        return false;
    }

    public boolean handleDeposit(CommandSender sender, Command cmd, String label, String[] args)
    {
        Player plr = (Player)sender;

        if( plr.getInventory().getItemInMainHand().getType() != Material.DIAMOND )
        {
            plr.sendMessage("You must be holding Diamonds to use this command!");
            return true;
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

    public boolean handleWithdraw(CommandSender sender, Command cmd, String label, String[] args)
    {
        Player plr = (Player)sender;

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

        for( int x = 0; x < amount / plr.getInventory().getMaxStackSize(); x++ )
            amounts.add(plr.getInventory().getMaxStackSize());
        amounts.add(amount % plr.getInventory().getMaxStackSize());

        int emptyCount = 0;
        for( ItemStack stack : plr.getInventory().getContents() )
            if( stack == null )
                emptyCount++;

        if( !config.getBoolean("AllowLittering") && emptyCount < amounts.size() )
        {
            plr.sendMessage("You do not have enough free space to do that!");
            return true;
        }

        int refundAmount = 0;

        EconomyResponse r = econ.withdrawPlayer(plr, amount * config.getInt("DiamondWorth"));
        if( r.transactionSuccess() )
        {
            for(Integer amt : amounts )
            {
                ItemStack diamonds = new ItemStack(Material.DIAMOND, amt);
                if( plr.getInventory().firstEmpty() != -1 )
                    plr.getInventory().addItem(diamonds);
                else if( config.getBoolean("AllowLittering") )
                    plr.getWorld().dropItem(plr.getLocation(), diamonds);
                else
                {
                    if( plr.getEnderChest().firstEmpty() != -1 )
                        plr.getEnderChest().addItem(diamonds);
                    else
                        refundAmount += diamonds.getAmount();
                }
            }
        }
        else
            plr.sendMessage(String.format("An error occured: %s", r.errorMessage));

        int refundCost = refundAmount * config.getInt("DiamondWorth");
        if( refundAmount != 0 )
        {
            r = econ.depositPlayer(plr, refundCost);
            if( !r.transactionSuccess() )
            {
                log.severe(String.format("Failed to refund player (%s) for %s", plr.getName(), econ.format(r.amount)));
            }
        }

        plr.sendMessage(String.format("You have withdrew %d Diamonds, for %s. Your balance is now %s",
                amount-refundAmount, econ.format((amount * config.getInt("DiamondWorth") - refundCost)), econ.format(econ.getBalance(plr))));

        return true;
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
