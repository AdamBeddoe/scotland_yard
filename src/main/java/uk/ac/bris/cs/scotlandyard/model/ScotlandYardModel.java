package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.Black;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import uk.ac.bris.cs.gamekit.graph.Graph;

public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor {
    private final List<Boolean> rounds;
    private final Graph<Integer, Transport> graph;
    private final  ScotlandYardPlayer mrX;
    private final List<PlayerConfiguration> startPlayers = new ArrayList<>();
    private final List<ScotlandYardPlayer> playerList = new ArrayList<>();
	private final Set<Colour> winners = new HashSet();
    private ScotlandYardPlayer currentPlayer;
    private Set<Move> availableMoves;
    private final List<Spectator> spectators = new ArrayList<>();
    private int roundNum = 0;
    private int playerNum = 0;
    private int lastKnownLocation = 0;
    private boolean gameOver = false;

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {
                this.rounds = requireNonNull(rounds);
                this.graph = requireNonNull(graph);
                this.startPlayers.add(requireNonNull(mrX));
                this.startPlayers.add(firstDetective);
				requireNonNull(firstDetective);

                for(PlayerConfiguration detective : restOfTheDetectives) {
                	this.startPlayers.add(requireNonNull(detective));
				}

				if (rounds.isEmpty()) throw new IllegalArgumentException("Empty rounds");
	 			if (graph.isEmpty()) throw new IllegalArgumentException("Empty map (graph)");
	 			if (mrX.colour != Black) throw new IllegalArgumentException("MrX should be Black");

	 			checkDuplicateLocations(startPlayers);
	 			checkDuplicateColours(startPlayers);
	 			checkPlayerTickets(startPlayers);

	 			for(PlayerConfiguration player : startPlayers){
	 				playerList.add(new ScotlandYardPlayer(player.player, player.colour, player.location, player.tickets));
				}

				this.mrX = playerList.get(0);
				this.currentPlayer = this.mrX;

			if (this.detectivesAllStuck() || this.validMoves(this.mrX).isEmpty()) gameOver = true;
	}

	// Checks all startPlayers for duplicate locations.
	private void checkDuplicateLocations(List<PlayerConfiguration> startPlayers) {
		Set<Integer> locations = new HashSet<>();
		for(PlayerConfiguration player : startPlayers) {
			if (locations.contains(player.location)) throw new IllegalArgumentException("Duplicate location");
			else locations.add(player.location);
		}
	}

	// Checks all startPlayers for duplicate colours.
    private void checkDuplicateColours(List<PlayerConfiguration> startPlayers) {
        Set<Colour> colours = new HashSet<>();
        for(PlayerConfiguration player : startPlayers) {
            if (colours.contains(player.colour)) throw new IllegalArgumentException("Duplicate colour");
            else colours.add(player.colour);
        }
    }

	// Validates the tickets of all startPlayers.
    private void checkPlayerTickets(List<PlayerConfiguration> startPlayers) {
		for (PlayerConfiguration player : startPlayers) {
			if (player.tickets.size() != 5) throw new IllegalArgumentException("Player does not have required tickets");
			if (!player.equals(this.startPlayers.get(0))) validateDetectiveTickets(player);
		}
    }

	// Checks a detective does not have any Mr X tickets.
	private void validateDetectiveTickets(PlayerConfiguration player) {
		if (player.tickets.get(Secret) != 0) throw new IllegalArgumentException("Detective has a Secret ticket");
		if (player.tickets.get(Double) != 0) throw new IllegalArgumentException("Detective has a Double ticket");
	}

	@Override
	public void registerSpectator(Spectator spectator) {
		if (spectators.contains(spectator)) throw new IllegalArgumentException("Already a spectator");
		else {
			spectators.add(requireNonNull(spectator));
		}
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		if (!spectators.contains((requireNonNull(spectator)))) throw new IllegalArgumentException("Not an existing spectator");
		else {
			spectators.remove(requireNonNull(spectator));
		}
	}

	@Override
	public void startRotate() {
        if (this.gameOver) throw new IllegalStateException("Game won");
        this.playerNum = 0;
        this.availableMoves = validMoves(this.mrX);
        if (this.availableMoves.isEmpty()) gameOver();
		Set<Move> playerMoves = unmodifiableSet(this.availableMoves);
		Player player = this.currentPlayer.player();
        notifyLoop(spectator -> spectator.onRoundStarted(this, getCurrentRound()));
        player.makeMove(this, this.currentPlayer.location(), playerMoves, this);
	}

	// Creates a set of valid moves for a detective.
	private Set<Move> validMoves(ScotlandYardPlayer player) {
		Collection<Edge<Integer,Transport>> edgesFrom = this.graph.getEdgesFrom(graph.getNode(player.location()));

		Set<TicketMove> firstMoves = edgesFrom.stream()
				.filter(edge -> !nodeOccupied(edge))
				.map(edge -> new TicketMove(player.colour(), Ticket.fromTransport(edge.data()), edge.destination().value()))
                .flatMap(ticketMove -> addSecretMoves(ticketMove, player).stream())
                .filter(move -> player.hasTickets(move.ticket()))
				.collect(Collectors.toSet());

        Set<DoubleMove> doubleMoves = firstMoves.stream()
                .flatMap(move -> doubleMovesFrom(move, player).stream())
                .filter(doubleMove -> canUseDouble(doubleMove, player))
                .collect(Collectors.toSet());

        Set<Move> validMoves = new HashSet<>();
        validMoves.addAll(firstMoves);
        validMoves.addAll(doubleMoves);

        return validMoves;
	}

	// Returns all double moves from the firstMove
    private Set<DoubleMove> doubleMovesFrom(TicketMove firstMove, ScotlandYardPlayer player) {
        Collection<Edge<Integer,Transport>> edgesFrom = this.graph.getEdgesFrom(graph.getNode(firstMove.destination()));

        Set<DoubleMove> doubleMovesFrom = edgesFrom.stream()
            .filter(edge -> !nodeOccupied(edge))
            .map(edge -> new TicketMove(firstMove.colour(), Ticket.fromTransport(edge.data()), edge.destination().value()))
            .flatMap(ticketMove -> addSecretMoves(ticketMove, player).stream())
            .map(secondMove -> new DoubleMove(firstMove.colour(), firstMove, secondMove))
            .collect(Collectors.toSet());

        return doubleMovesFrom;
    }

    // Returns a set containing the original move and the corresponding secret move if player has a secret ticket
    private Set<TicketMove> addSecretMoves(TicketMove originalMove, ScotlandYardPlayer player) {
	    Set<TicketMove> newMoves = new HashSet<>();
	    newMoves.add(originalMove);
	    if (player.hasTickets(Secret)) newMoves.add(new TicketMove(Black, Secret, originalMove.destination()));
	    return newMoves;
    }

    // Returns true if the player is able to use the double ticket
    private boolean canUseDouble(DoubleMove doubleMove, ScotlandYardPlayer player) {
	    boolean sameTicketValid = true;
	    if (doubleMove.hasSameTicket()) {
	        sameTicketValid = player.hasTickets(doubleMove.firstMove().ticket(),2);
        }
	    return (player.hasTickets(doubleMove.firstMove().ticket())
                && player.hasTickets(doubleMove.secondMove().ticket())
                && player.hasTickets(Double)
                && this.roundNum <= (rounds.size()-2)
                && sameTicketValid);
    }

    // Returns true if the destination of an edge is occupied by a detective.
	private Boolean nodeOccupied(Edge edge) {
		for (ScotlandYardPlayer player : playerList) {
			if ((int)edge.destination().value() == player.location() && !player.isMrX()) return true;
			}
		return false;
	}

	@Override
	public Collection<Spectator> getSpectators() {
		return  Collections.unmodifiableCollection(spectators);
	}

	@Override
	public List<Colour> getPlayers() {
		List<Colour> colours = new ArrayList<>();

		for(PlayerConfiguration player : startPlayers){
			colours.add(player.colour);
		}

		return Collections.unmodifiableList(colours);
	}

	@Override
	public Set<Colour> getWinningPlayers() {
	    return Collections.unmodifiableSet(this.winners);
	}

	@Override
	public int getPlayerLocation(Colour colour) {
		int location = 0;

		for(ScotlandYardPlayer player : playerList) {
			if(player.colour().equals(Black)){
				location = this.lastKnownLocation;
			}
			else if(player.colour().equals(colour)) {
				location = player.location();
			}
		}

		return location;
	}

	@Override
	public int getPlayerTickets(Colour colour, Ticket ticket) {
		for(ScotlandYardPlayer player : playerList) {
			if(player.colour().equals(colour)) {
				return player.tickets().get(ticket);
			}
		}

		return -1;
	}

	@Override
	public boolean isGameOver() {
		return this.gameOver;
	}

	@Override
	public Colour getCurrentPlayer() {
		return currentPlayer.colour();
	}

	@Override
	public int getCurrentRound() {
		return this.roundNum;
	}

	@Override
	public boolean isRevealRound() {
		return rounds.get(this.roundNum);
	}

	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		return new ImmutableGraph(graph);
	}

	@Override
	public void accept(Move move) {
	    requireNonNull(move);

        if (!this.availableMoves.contains(move)) throw new IllegalArgumentException("Move not in valid moves");
        move.visit(this);

        this.playerNum++;
        if (playerNum < this.playerList.size()) {
            this.currentPlayer = this.playerList.get(playerNum);
            Player player = this.currentPlayer.player();
            this.availableMoves = validMoves(this.currentPlayer);
            if (availableMoves.isEmpty()) availableMoves.add(new PassMove(currentPlayer.colour()));
            Set<Move> playerMoves = unmodifiableSet(this.availableMoves);
			if (!this.gameOver) player.makeMove(this, this.currentPlayer.location(), playerMoves, this);
		}
        else {
            this.currentPlayer = this.mrX;
            if (this.roundNum >= this.rounds.size()) gameOver();
            if (validMoves(this.mrX).isEmpty()) gameOver();
            if (detectivesAllStuck()) gameOver();
            if (!isGameOver()) notifyLoop(spectator -> spectator.onRotationComplete(this));
		}
    }

	public void visit(TicketMove move) {
		this.currentPlayer.removeTicket(move.ticket());
		this.currentPlayer.location(move.destination());
		TicketMove newMove = move;

		if (this.currentPlayer.isMrX()) {
            if(isRevealRound()) {
            	this.lastKnownLocation = this.currentPlayer.location();
            }
            else {
            	newMove = new TicketMove(Black, move.ticket(), this.lastKnownLocation);
			}
            this.roundNum++;
            notifyLoop(spectator -> spectator.onRoundStarted(this, roundNum));
        }

		final TicketMove finalMove = newMove;
        notifyLoop(spectator -> spectator.onMoveMade(this, finalMove));

        if (this.currentPlayer.isDetective()) {

            if (this.currentPlayer.location() == this.mrX.location() && roundNum > 0) gameOver();
            this.mrX.addTicket(move.ticket());
        }
	}

	public void visit(DoubleMove move) {
		this.currentPlayer.removeTicket(Double);

		int dest1 = move.firstMove().destination();
		int dest2 = move.finalDestination();

		if (!rounds.get(this.roundNum)) dest1 = this.lastKnownLocation;
		else this.lastKnownLocation = dest1;
		if (!rounds.get(this.roundNum+1)) dest2 = this.lastKnownLocation;

		DoubleMove newMove = new DoubleMove(Black, move.firstMove().ticket(), dest1, move.secondMove().ticket(), dest2);

		notifyLoop(spectator -> spectator.onMoveMade(this, newMove));
		move.firstMove().visit(this);
		move.secondMove().visit(this);
	}

    public void visit(PassMove move) {
        notifyLoop(spectator -> spectator.onMoveMade(this, move));
    }

    private void gameOver() {
	    this.gameOver = true;
	    if (this.currentPlayer.isMrX()) this.winners.add(Black);
	    else {
	        for (ScotlandYardPlayer player: playerList) {
	            if (player.colour() != Black) this.winners.add(player.colour());
            }
        }
        notifyLoop(spectator -> spectator.onGameOver(this, this.winners));
    }

    private boolean detectivesAllStuck() {
	    boolean areStuck = true;
	    for (ScotlandYardPlayer player : playerList) {
	        if (!player.isMrX() && !validMoves(player).isEmpty()) areStuck = false;
        }
        return areStuck;
    }

    private void notifyLoop(NotifyFunction function) {
        for (Spectator spectator : spectators){
            function.notifyFunc(spectator);
        }
    }
}

interface NotifyFunction {
	void notifyFunc(Spectator spectator);
}
