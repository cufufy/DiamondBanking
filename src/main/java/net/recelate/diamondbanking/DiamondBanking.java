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

        // Config handling starts
        this.saveDefaultConfig(); // Ensures config.yml from JAR is in plugin's data folder if not already.
        config = this.getConfig(); // Load it.

        boolean defaultsApplied = false;
        if (!config.contains("DiamondWorth")) {
            getLogger().info("DiamondWorth not found in config.yml, setting default value (1).");
            config.set("DiamondWorth", 1);
            defaultsApplied = true;
        }
        // Ensure other defaults are set if this is a fresh config or they are missing
        if (!config.contains("AllowLittering")) {
            // Assuming 'AllowLittering' should also be explicitly defaulted if missing
            // If DiamondWorth was missing, it's a good sign other defaults might be too.
            getLogger().info("AllowLittering not found in config.yml, setting default value (true).");
            config.set("AllowLittering", true);
            defaultsApplied = true;
        }

        if (defaultsApplied) {
            this.saveConfig(); // Save these initial defaults if any were applied.
        }
        // Config handling ends
=======
        config.addDefault("DiamondWorth", 1);
    }

    @Override
    public void onDisable()
    {
        log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
        if( config != null )
        {
            // config.options().copyDefaults(true); // Ensure this line is removed
            saveConfig();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getLabel().equalsIgnoreCase("deposit")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be run by a player.");
                return true;
            }
            return handleDeposit(sender, cmd, label, args);
        } else if (cmd.getLabel().equalsIgnoreCase("withdraw")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be run by a player.");
                return true;
            }
            return handleWithdraw(sender, cmd, label, args);
        } else if (cmd.getLabel().equalsIgnoreCase("dbreload")) {
            if (!sender.hasPermission("diamondbanking.reload")) {
                sender.sendMessage("You do not have permission to use this command.");
                return true;
            }
            reloadPluginConfig(sender);
            return true;
        }
        return false;
    }

    public boolean handleDeposit(CommandSender sender, Command cmd, String label, String[] args)
    {
        Player plr = (Player)sender;

        if( plr.getInventory().getItemInMainHand().getType() != Material.DIAMOND &&
            plr.getInventory().getItemInMainHand().getType() != Material.DIAMOND_BLOCK )
        {
            plr.sendMessage("You must be holding Diamonds to use this command!");
            return true;
        }

        int amount = plr.getInventory().getItemInMainHand().getAmount();
        if( plr.getInventory().getItemInMainHand().getType() == Material.DIAMOND_BLOCK )
            amount *= 9;

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

    public void reloadPluginConfig(CommandSender sender) {
        // Reload the configuration from disk
        this.reloadConfig();
        // The 'config' object is now updated with values from config.yml

        // Re-apply default setting logic (similar to onEnable)
        // This ensures that if a user manually deletes a key from config.yml and reloads,
        // the default value is reinstated and saved.
        boolean defaultsApplied = false;
        if (!config.contains("DiamondWorth")) {
            getLogger().info("DiamondWorth not found in config.yml during reload, setting default value (1) and saving.");
            config.set("DiamondWorth", 1);
            defaultsApplied = true;
        }
        if (!config.contains("AllowLittering")) {
            getLogger().info("AllowLittering not found in config.yml during reload, setting default value (true) and saving.");
            config.set("AllowLittering", true);
            defaultsApplied = true;
        }

        if (defaultsApplied) {
            this.saveConfig(); // Save if any defaults were applied
            sender.sendMessage("§aDiamondBanking configuration reloaded. Missing values were reset to defaults and saved.");
        } else {
            sender.sendMessage("§aDiamondBanking configuration reloaded successfully.");
        }
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
}
