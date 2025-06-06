package net.recelate.diamondbanking;

import java.util.ArrayList;
import java.util.HashMap; // Added for addItem return type
import java.util.List;
import java.util.logging.Logger;

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
    private static final int DIAMOND_WORTH = 1;

    @Override
    public void onEnable()
    {
        if( !setupEconomy() )
        {
            log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public void onDisable()
    {
        log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
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

        EconomyResponse r = econ.depositPlayer(plr, amount * DIAMOND_WORTH);
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
        if( econ.getBalance(plr) < amount * DIAMOND_WORTH )
        {
            plr.sendMessage("You do not have enough money to do that!");
            return true;
        }

        // Calculate available inventory space
        int availableSpace = 0;
        for (ItemStack item : plr.getInventory().getStorageContents()) {
            if (item == null) {
                availableSpace += Material.DIAMOND.getMaxStackSize();
            } else if (item.getType() == Material.DIAMOND) {
                availableSpace += item.getMaxStackSize() - item.getAmount();
            }
        }

        if (amount > availableSpace) {
            if (availableSpace == 0) {
                plr.sendMessage("You do not have enough inventory space to withdraw any diamonds.");
                return true;
            }
            plr.sendMessage(String.format("You only have enough space for %d diamonds. Withdrawing that amount instead.", availableSpace));
            amount = availableSpace;
        }

        EconomyResponse r = econ.withdrawPlayer(plr, amount * DIAMOND_WORTH);
        if (!r.transactionSuccess()) {
            plr.sendMessage(String.format("An error occurred during withdrawal: %s", r.errorMessage));
            return true;
        }

        int diamondsToGive = amount;
        int diamondsActuallyGiven = 0;

        if (diamondsToGive > 0) {
            ItemStack diamonds = new ItemStack(Material.DIAMOND, diamondsToGive);
            HashMap<Integer, ItemStack> notAdded = plr.getInventory().addItem(diamonds);
            diamondsActuallyGiven = diamondsToGive - (notAdded.isEmpty() ? 0 : notAdded.get(0).getAmount());
            diamondsToGive -= diamondsActuallyGiven;
        }

        if (diamondsToGive > 0) {
            ItemStack remainingDiamondsForEnderChest = new ItemStack(Material.DIAMOND, diamondsToGive);
            HashMap<Integer, ItemStack> notAddedToEnderChest = plr.getEnderChest().addItem(remainingDiamondsForEnderChest);
            int diamondsGivenToEnderChest = diamondsToGive - (notAddedToEnderChest.isEmpty() ? 0 : notAddedToEnderChest.get(0).getAmount());
            diamondsActuallyGiven += diamondsGivenToEnderChest;
            diamondsToGive -= diamondsGivenToEnderChest;
        }

        int refundAmount = diamondsToGive;
        if (refundAmount > 0) {
            int refundValue = refundAmount * DIAMOND_WORTH;
            econ.depositPlayer(plr, refundValue); // Refund the value of diamonds that couldn't be placed
            plr.sendMessage(String.format("Refunded %s for %d diamonds that could not be placed in your inventory or ender chest.", econ.format(refundValue), refundAmount));
        }

        plr.sendMessage(String.format("You have withdrawn %d Diamonds, for %s. Your balance is now %s",
                diamondsActuallyGiven, econ.format(diamondsActuallyGiven * DIAMOND_WORTH), econ.format(econ.getBalance(plr))));

        return true;
    }

    public void reloadPluginConfig(CommandSender sender) {
        sender.sendMessage("§aDiamondBanking uses fixed settings and does not require a reload.");
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
