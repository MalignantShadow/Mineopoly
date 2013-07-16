package com.kill3rtaco.mineopoly.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import org.bukkit.entity.Player;

import com.kill3rtaco.mineopoly.Mineopoly;
import com.kill3rtaco.mineopoly.game.chat.MineopolyChannelListener;
import com.kill3rtaco.mineopoly.game.sections.ActionProvoker;
import com.kill3rtaco.mineopoly.game.sections.OwnableSection;
import com.kill3rtaco.mineopoly.game.sections.Property;
import com.kill3rtaco.mineopoly.game.sections.Railroad;
import com.kill3rtaco.mineopoly.game.sections.SpecialSquare;
import com.kill3rtaco.mineopoly.game.sections.Utility;
import com.kill3rtaco.mineopoly.game.sections.squares.FreeParkingSquare;
import com.kill3rtaco.mineopoly.game.sections.squares.JailSquare;

import com.kill3rtaco.tacoapi.TacoAPI;

public class MineopolyPlayer extends MineopolyChannelListener{

	private boolean jailed = false;
	private boolean canRoll;
	private boolean hasRolled = false;
	private boolean hasTurn = false;
	private boolean needsGoMoney = false;
	private boolean payRent = true;
	private boolean landedOnGo = false;
	private boolean endTurnAuto = false;
	private int money = 1500;
	private int roll1;
	private int roll2;
	private int totalRolls = 0;
	private int doubleRolls = 0;
	private int jailRolls = 0;
	private boolean chanceJailCard;
	private boolean ccJailCard;
	private MineopolySection sectionOn;
	private ArrayList<OwnableSection> ownedSections = new ArrayList<OwnableSection>();
	private ArrayList<MineopolyColor> monopolies = new ArrayList<MineopolyColor>();

	public MineopolyPlayer(Player player) {
		super(player);
	}
	
	public void setPlayer(Player player){
		this.player = player;
	}
	
	public void setBalance(int amount){
		this.money = amount;
		sendBalanceMessage();
	}
	
	public void setTurn(boolean turn, boolean supressDoubles){
		this.hasTurn = turn;
		this.canRoll = turn;
		this.hasRolled = !turn;
		if(!turn){
			if(roll1 == roll2 && !supressDoubles && !jailed){
				this.hasTurn = true;
				this.canRoll = true;
				Mineopoly.plugin.getGame().getChannel().sendMessage("&b" + getName() + "&3 has ended their turn but rolled doubles, rolling again...");
				roll();
			}else{
				Mineopoly.plugin.getGame().nextPlayer();
			}
		}
	}
	
	/**
	 * Gets the amount of money that this player has. Note that this money is separate from any money
	 * earned in the server economy, as the game uses a separate economy to play the game.
	 * @return The amount of money this player has
	 */
	public int getBalance(){
		return this.money;
	}
	
	public boolean hasRolled(){
		return this.hasRolled;
	}
	
	public boolean hasTurn(){
		return this.hasTurn;
	}
	
	private void payRent(){
		if(sectionOn instanceof OwnableSection){
			OwnableSection section = (OwnableSection)sectionOn;
			if(section.isOwned()){
				if(section.getOwner().isJailed()){
					Mineopoly.plugin.getGame().getChannel().sendMessage("&b" + section.getOwner() + " &3cannot collect rent from &b" + getName() + 
							" &3for landing on " + sectionOn.getColorfulName() + " &3because they are &1jailed", section.getOwner());
					section.getOwner().sendMessage("&3You cannot collect rent from &b" + getName() + " &3for landing on " + sectionOn.getColorfulName() +
							" &3because you are &1jailed");
				}else{
					if(!section.getOwner().getName().equalsIgnoreCase(getName())){
						takeMoney(section.getRent());
						section.getOwner().addMoney(section.getRent());
						Mineopoly.plugin.getGame().getChannel().sendMessage("&b" + getName() + " &3paid &b" + section.getOwner().getName() +
								" &2" + section.getRent() + " &3for landing on " + sectionOn.getColorfulName(), this);
						sendMessage("&3You paid &b" + section.getOwner() + " &2" + section.getRent() + " &3for landing on " + sectionOn.getColorfulName());
					}
				}
			}
		}
	}
	
	public boolean canBuy(OwnableSection section){
		return getBalance() >= section.getPrice();
	}
	
	public boolean canAddHouse(Property section){
		return getBalance() >= section.getHousePrice();
	}
	
	public boolean canAddHotel(Property section){
		return getBalance() >= section.getHotelPrice();
	}
	
	public void getInfo(Player p){
		int cards = 0;
		if(hasChanceJailCard()) cards++;
		if(hasCommunityChestJailCard()) cards++;
		Mineopoly.plugin.chat.sendPlayerMessageNoHeader(p, TacoAPI.getChatUtils().createHeader("&3Mineopoly&7: &b" + getName()));
		Mineopoly.plugin.chat.sendPlayerMessageNoHeader(p, "&3Money&7: &2" + getBalance());
		Mineopoly.plugin.chat.sendPlayerMessageNoHeader(p, "&3Rolls&7: &2" + getTotalRolls() + " &3On space: " + getCurrentSection().getColorfulName() + " &3(&b" + getCurrentSection().getId() + "&3)" +
				(getCurrentSection().getId() == 10 ? (isJailed() ? "&7[&cIn Jail&7]" : "&7[&aNot in Jail&7])") : ""));
		Mineopoly.plugin.chat.sendPlayerMessageNoHeader(p, "&3Properties&7: &b" + ownedPropertiesSize());
		Mineopoly.plugin.chat.sendPlayerMessageNoHeader(p, "&3Monopolies&7: &b" + monopolySize());
		Mineopoly.plugin.chat.sendPlayerMessageNoHeader(p, "&3Get Out of Jail Free cards&7: &2" + (cards > 0 ? cards : "none"));
		Mineopoly.plugin.chat.sendPlayerMessageNoHeader(p, "");
		Mineopoly.plugin.chat.sendPlayerMessageNoHeader(p, "&8My properties: &7/mineopoly deeds " + getName());
		Mineopoly.plugin.chat.sendPlayerMessageNoHeader(p, "&8My monopolies: &7/mineopoly monopolies " + getName());
	}
	
	public boolean hasMonopoly(MineopolyColor color){
		if(monopolies.contains(color))
			return true;
		else
			return false;
	}
	
	public void addMoney(int amount){
		addMoney(amount, true);
	}
	
	public void addMoney(int amount, boolean sendMessage){
		this.money += amount;
		if(sendMessage) sendBalanceMessage();
	}
	
	public void takeMoney(int amount){
		takeMoney(amount, true);
	}
	
	public void takeMoney(int amount, boolean sendMessage){
		this.money -= amount;
		if(sendMessage) sendBalanceMessage();
	}
	
	public void sendBalanceMessage(){
		sendMessage("&3Your balance is now &2" + getBalance());
	}
	
	public void payPot(int amount){
		payPot(amount, true);
	}
	
	public void payPot(int amount, boolean sendMessage){
		this.money -= amount;
		Mineopoly.plugin.getGame().getBoard().getPot().addMoney(amount);
		if(sendMessage) sendBalanceMessage();
	}
	
	public boolean hasMoney(int amount){
		return getBalance() >= amount;
	}
	
	public void payPlayer(MineopolyPlayer player, int amount, boolean sendMessageSender, boolean sendMessageReceiver){
		takeMoney(amount, sendMessageSender);
		player.addMoney(amount, sendMessageReceiver);
	}
	
	public void roll(){
		this.totalRolls++;
		this.canRoll = false;
		this.hasRolled = true;
		Random random = new Random();
		this.roll1 = random.nextInt(6) + 1;
		this.roll2 = random.nextInt(6) + 1;
		int sum = roll1 + roll2 + getCurrentSection().getId();
		if(sum > 39){
			sum -= 40;
		}
		
		if(roll1 == roll2){
			doubleRolls++;
			if(doubleRolls == 3){
				doubleRolls = 0;
				setJailed(true, true);
				Mineopoly.plugin.getGame().getChannel().sendMessage("&b" + getName() + "&3 was jailed because they rolled doubles 3 times in a row", this);
				sendMessage("&3You were jailed because you rolled doubles 3 times in a row");
			}else{
				moveForward(sum);
			}
		}else{
			doubleRolls = 0;
			moveForward(sum);
		}
	}
	
	public int getHotels(){
		int hotels = 0;
		for(MineopolySection s : ownedSections()){
			if(s instanceof Property){
				if(((Property) s).hasHotel()) hotels ++;
			}
		}
		return hotels;
	}
	
	public int getHouses(){
		int houses = 0;
		for(MineopolySection s : ownedSections()){
			if(s instanceof Property){
				houses += ((Property) s).getHouses();
			}
		}
		return houses;
	}
	
	private void moveForward(int forward){
		MineopolySection next = Mineopoly.plugin.getGame().getBoard().getSection(forward);
		Mineopoly.plugin.getGame().getChannel().sendMessage("&b" + getName() + " &3rolled a &b" + roll1 + "&3 and a &b" + roll2, this);
		if(!(getCurrentSection() instanceof FreeParkingSquare))
			Mineopoly.plugin.getGame().getChannel().sendMessage("&b" + getName() + " &3landed on " + next.getColorfulName(), this);
		sendMessage("&3You rolled a &b" + roll1 + "&3 and a &b" + roll2);
		if(!(getCurrentSection() instanceof FreeParkingSquare))
			sendMessage("&3You landed on " + next.getColorfulName());
		setCurrentSection(next);
	}
	
	public void moveToNearestRailroad(){
		int id = getCurrentSection().getId();
		if(id > 35 || id < 5)
			setCurrentSection(Mineopoly.plugin.getGame().getBoard().getSection(5));
		else if(id > 5 && id < 15)
			setCurrentSection(Mineopoly.plugin.getGame().getBoard().getSection(15));
		else if(id > 15 && id < 25)
			setCurrentSection(Mineopoly.plugin.getGame().getBoard().getSection(25));
		else if(id > 25 && id < 35)
			setCurrentSection(Mineopoly.plugin.getGame().getBoard().getSection(35));
	}
	
	public void moveToNearestUtility(){
		int id = getCurrentSection().getId();
		if(id < 12 || id > 24)
			setCurrentSection(Mineopoly.plugin.getGame().getBoard().getSection(12));
		else
			setCurrentSection(Mineopoly.plugin.getGame().getBoard().getSection(24));
	}
	
	public void setJailRolls(int rolls){
		jailRolls = rolls;
	}
	
	public int getJailRolls(){
		return jailRolls;
	}
	
	public int getTotalRolls(){
		return totalRolls;
	}
	
	public void setTotalRolls(int rolls){
		this.totalRolls = rolls;
	}
	
	public boolean canRoll(){
		return this.canRoll;
	}
	
	public boolean canEndTurnAutomatically(){
		return this.endTurnAuto;
	}
	
	public void setCanEndTurnAutomatically(boolean endTurn){
		this.endTurnAuto = endTurn;
	}
	
	public void moveWithoutRent(MineopolySection section){
		payRent = false;
		setCurrentSection(section);
	}
	
	public void setCurrentSection(MineopolySection section){
		setCurrentSection(section, true);
	}
	
	public void setCurrentSection(MineopolySection section, boolean goMoney){
		setCurrentSection(section, goMoney, true);
	}
	
	public void setCurrentSection(MineopolySection section, boolean process, boolean goMoney){
		setCurrentSection(section, goMoney, process, true);
	}
	
	public void setCurrentSection(MineopolySection section, boolean goMoney, boolean process, boolean endAuto){
		if(process){
			int lastId = 0;
			if(this.sectionOn != null)
				lastId = this.sectionOn.getId();
			this.sectionOn = section;
			
			if(section.getId() < lastId && section.getId() != 0){
				if(section instanceof JailSquare){
					if(isJailed()){
						needsGoMoney = false;
					}else{
						needsGoMoney = true;
					}
				}else{
					needsGoMoney = true;
				}
			}else if(section.getId() > lastId){
				if(landedOnGo && totalRolls > 1){
					needsGoMoney = true;
					landedOnGo = false;
				}
			}
			
			if(!goMoney) needsGoMoney = false;
			
			if(needsGoMoney){
				Mineopoly.plugin.getGame().getChannel().sendMessage("&b" + getName() + " &3passed &6Go &3and was given &2200", this);
				sendMessage("&3You passed &6Go &3and were given &2200");
				addMoney(200);
				needsGoMoney = false;
			}

			if(section instanceof SpecialSquare){
				if(section instanceof JailSquare){
					JailSquare js = (JailSquare) section;
					if(isJailed()){
						TacoAPI.getPlayerAPI().teleport(getPlayer(), js.getJailCellLocation());
					}else{
						TacoAPI.getPlayerAPI().teleport(getPlayer(), js.getJustVisitingLocation());
						endTurnAuto = true;
					}
				}else{
					TacoAPI.getPlayerAPI().teleport(getPlayer(), section.getLocation());
					SpecialSquare square = (SpecialSquare) section;
					square.provokeAction(this);
				}
			}else if(section instanceof ActionProvoker){
				TacoAPI.getPlayerAPI().teleport(getPlayer(), section.getLocation());
				ActionProvoker ss = (ActionProvoker) section;
				ss.provokeAction(this);
			}else if(section instanceof OwnableSection){
				TacoAPI.getPlayerAPI().teleport(getPlayer(), section.getLocation());
				if(this.payRent && !((OwnableSection) section).isMortgaged())
					payRent();
				this.payRent = true;
				OwnableSection ownable = (OwnableSection) section;
				if(!ownable.isOwned()){
					String type;
					if(ownable instanceof Property) type = "property";
					else if(ownable instanceof Railroad) type = "railroad";
					else if(ownable instanceof Utility) type = "utility";
					else type = "space";
					if(canBuy(ownable)){										
						sendMessage("&3You can buy this &b" + type + " &3for &2" + ownable.getPrice() + " &3with &b/" + Mineopoly.P_ALIAS + " buy");
					}else{
						sendMessage("&3You do not have enough money to buy this &b" + type + " (&2" + ownable.getPrice() + "&b)");
					}
				}else{
					endTurnAuto = true;
				}
			}
			landedOnGo = false;
			if(section.getId() == 0) landedOnGo = true;
			sendMessage("&3Type &b/" + Mineopoly.P_ALIAS + " info &3to view information for this space");
			if(endTurnAuto && endAuto){
				boolean ate = Mineopoly.config.getAllowAutomaticTurnEnding();
				if(ate){
					sendMessage("&aTurn ended automatically");
					getPlayer().chat("/mineopoly end-turn");
				}else{
					sendMessage("&3End your turn with &b/" + Mineopoly.M_ALIAS + " et");
				}
				endTurnAuto = false;
			}else if(endAuto && !isJailed()){
				if(totalRolls > 0) sendMessage("&3End your turn with &b/" + Mineopoly.M_ALIAS + " et");
			}
		}else{
			this.sectionOn = section;
			TacoAPI.getPlayerAPI().teleport(getPlayer(), section.getLocation());
		}
	}
	
	public void move(int amount){
		boolean goMoney = true;
		if(amount < 0) goMoney = false;
		int lastId = getCurrentSection().getId();
		MineopolySection next = Mineopoly.plugin.getGame().getBoard().getSection(lastId + amount);
		setCurrentSection(next, goMoney);
	}
	
	public int getOwnedPropertiesWithColor(MineopolyColor color){
		int amount = 0;
		for(MineopolySection s : ownedSections()){
			if(s instanceof Property){
				Property p = (Property) s;
				if(p.getColor() == color)
					amount++;
			}
		}
		return amount;
	}
	
	public boolean ownsCurrentSection(){
		if(getCurrentSection() instanceof OwnableSection){
			OwnableSection section = (OwnableSection) getCurrentSection();
			return ownsSection(section);
		}else{
			return false;
		}
	}
	
	public int ownedRailRoads(){
		int rr = 0;
		for(Railroad r : Mineopoly.plugin.getGame().getBoard().getRailroads()){
			if(this.ownsSection((OwnableSection) r))
				rr++;
		}
		return rr;
	}
	
	public int ownedUtilities(){
		int utils = 0;
		for(Utility u : Mineopoly.plugin.getGame().getBoard().getUtilities()){
			if(this.ownsSection((OwnableSection) u))
					utils++;
		}
		return utils;
	}
	
	public void setJailed(boolean jailed, boolean ignoreDoubles){
		this.jailRolls = 0;
		this.jailed = jailed;
		setCurrentSection(Mineopoly.plugin.getGame().getBoard().getSection(10), false, true, false);
		setTurn(false, ignoreDoubles);
	}
	
	public boolean isJailed(){
		return this.jailed;
	}
	
	public void giveChanceJailCard(){
		chanceJailCard = true;
	}
	
	public void takeChanceJailCard(){
		chanceJailCard = false;
	}
	
	public boolean hasChanceJailCard(){
		return chanceJailCard;
	}
	
	public void giveCommunityChestJailCard(){
		ccJailCard = true;
	}
	
	public void takeCommunityChestJailCard(){
		ccJailCard = false;
	}
	
	public boolean hasCommunityChestJailCard(){
		return ccJailCard;
	}
	
	public MineopolySection getCurrentSection(){
		return this.sectionOn;
	}
	
	public boolean ownsSection(OwnableSection section){
		return ownedSections.contains(section);
	}
	
	public void addSection(OwnableSection section){
		ownedSections.add(section);
		if(section instanceof Property){
			Property p = (Property) section;
			if(p.getColor() == MineopolyColor.BLUE || p.getColor() == MineopolyColor.PURPLE){
				if(getOwnedPropertiesWithColor(p.getColor()) == 2)
					monopolies.add(p.getColor());
			}else{
				if(getOwnedPropertiesWithColor(p.getColor()) == 3)
					monopolies.add(p.getColor());
			}
		}
	}
	
	public ArrayList<OwnableSection> ownedSections(){
		return ownedSections;
	}
	
	public int ownedPropertiesSize(){
		return ownedSections.size();
	}
	
	public int valueOfOwnedSections(){
		int amount = 0;
		for(OwnableSection o : ownedSections()){
			amount += o.getPrice();
			if(o instanceof Property){
				Property p = (Property) o;
				if(p.hasHotel()){
					amount += p.getHotelPrice();
				}else if(p.getHouses() > 0){
					amount += p.getHousePrice() * p.getHouses();
				}
			}
		}
		return amount;
	}
	
	public ArrayList<Property> getPropertiesInMineopoly(MineopolyColor color){
		ArrayList<Property> result = new ArrayList<Property>();
		for(OwnableSection s : ownedSections){
			if(s instanceof Property){
				Property p = (Property) s;
				if(p.getColor() == color)
					result.add(p);
			}
		}
		return result;
	}
	
	public int monopolySize(){
		return monopolies.size();
	}
	
	public ArrayList<MineopolyColor> getMonopolies(){
		Collections.sort(monopolies);
		return monopolies;
	}
	
	public OwnableSection getOwnableSection(int id){
		for(OwnableSection o : ownedSections){
			if(o.getId() == id){
				return o;
			}
		}
		return null;
	}
	
	public String toString(){ return getName(); }
	
}