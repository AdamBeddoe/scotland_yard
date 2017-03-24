package uk.ac.bris.cs.scotlandyard.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.Black;
import static uk.ac.bris.cs.scotlandyard.model.Colour.Blue;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;

import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.Node;

import javax.print.attribute.standard.Destination;

// TODO implement all methods and pass all tests
public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor {
    private List<Boolean> rounds;
    private Graph<Integer, Transport> graph;
    private ScotlandYardPlayer mrX;
    private PlayerConfiguration firstDetective;
    private List<PlayerConfiguration> startPlayers = new ArrayList<>();
    private List<ScotlandYardPlayer> playerList = new ArrayList<>();
    private ScotlandYardPlayer currentPlayer;
    private Set<Move> availableMoves;
    private List<Spectator> spectators = new ArrayList<>();
    private int roundNum = 0;
    private int playerNum = 0;
    private int lastKnownLocation = 0;
    private boolean gameOver = false;
    private Set<Colour> winners = new HashSet();

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {
                this.rounds = requireNonNull(rounds);
                this.graph = requireNonNull(graph);
                this.startPlayers.add(requireNonNull(mrX));
                this.firstDetective = requireNonNull(firstDetective);
                this.startPlayers.add(firstDetective);

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
		System.out.println("Round" + roundNum);
		for (ScotlandYardPlayer player : playerList) {
			System.out.println("Player: " +  player.colour() + "Loc :" + player.location());
		}
        if (this.gameOver) throw new IllegalStateException("Game won");
        this.playerNum = 0;
        this.availableMoves = validMovesMrX();
        if (this.availableMoves.isEmpty()) gameOver();
		Set<Move> playerMoves = unmodifiableSet(this.availableMoves);
		Player player = this.currentPlayer.player();
        notifyLoop(spectator -> spectator.onRoundStarted(this, getCurrentRound()));
        player.makeMove(this, this.currentPlayer.location(), playerMoves, this);
	}

	// Creates a set of valid moves for a detective.
	private Set<Move> validMoves(ScotlandYardPlayer player) {

        Set<Move> validMoves = new HashSet<>();
        int loc = player.location();
        Node node = this.graph.getNode(loc);
        Collection<Edge> edges = this.graph.getEdgesFrom(node);

        for (Edge edge : edges) {
            Transport t = (Transport) edge.data();
            Ticket ticket = Ticket.fromTransport(t);
            if (player.hasTickets(ticket) && !nodeOccupied(edge)) {
                Move move = new TicketMove(player.colour(), ticket, (Integer) edge.destination().value());
				validMoves.add(move);
            }

        }
		if (player.colour().equals(Blue)) {
			System.out.println(validMoves);
		}
        return validMoves;
	}

	// Creates a set of valid moves for MrX.
    private Set<Move> validMovesMrX() {
		Set<Move> firstMoves = validMoves(this.mrX);
        Set<Move> validMoves = new HashSet<>();
		int loc = this.mrX.location();
		Node node = this.graph.getNode(loc);

		Collection<Edge> firstEdges = this.graph.getEdgesFrom(node);
		for (Edge firstEdge : firstEdges) {
			if (this.mrX.hasTickets(Secret) && !nodeOccupied(firstEdge)) {
				Move regularMove = new TicketMove(Black, Secret, (Integer) firstEdge.destination().value());
				firstMoves.add(regularMove);
			}

            if (this.mrX.hasTickets(Double) && this.roundNum <= (rounds.size()-2)) {
                for (Move firstMove : firstMoves) {
                    for (Move secondMove : validMovesFrom(firstMove)) {
                        Move doubleMove = new DoubleMove(Black, (TicketMove) firstMove, (TicketMove) secondMove);
                        validMoves.add(doubleMove);
                    }
                }
            }
		}
        validMoves.addAll(firstMoves);
		return validMoves;
    }

    // Creates a set of possible further moves following each move (for double moves).
    private Set<Move> validMovesFrom(Move move) {
        Set<Move> validMoves = new HashSet<>();
        if (move instanceof TicketMove) {
            Node node = this.graph.getNode(((TicketMove) move).destination());
            Collection<Edge> edges = this.graph.getEdgesFrom(node);
            int numTickets;

            for (Edge edge : edges) {
                Transport t = (Transport) edge.data();
                Ticket ticket = Ticket.fromTransport(t);
                if (((TicketMove) move).ticket().equals(ticket)) numTickets = 2;
                else numTickets = 1;

                if (currentPlayer.hasTickets(ticket, numTickets)  && !nodeOccupied(edge)) {
					Move newMove = new TicketMove(currentPlayer.colour(), ticket, (Integer) edge.destination().value());
                    validMoves.add(newMove);
                    if (currentPlayer.hasTickets(Secret)) {
                        Move secretMove = new TicketMove(Black, Secret, (Integer) edge.destination().value());
                        validMoves.add(secretMove);
                    }
                }
            }
        }
        return validMoves;
    }

    // Returns true if the destination of an edge is occupied by a detective.
	private Boolean nodeOccupied(Edge edge) {
		for (ScotlandYardPlayer player : playerList) {
			if (edge.destination().value() == (Integer)player.location() && !player.isMrX()) return true;
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
	    if (this.roundNum == 0) {
	        if (this.detectivesAllStuck()) gameOver = true;
	        if (this.validMovesMrX().isEmpty()) gameOver = true;
        }
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

		if (this.currentPlayer.colour().equals(Blue)) {
			System.out.println(move.toString());
		}

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
            if (validMovesMrX().isEmpty()) gameOver();
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
        	if (this.currentPlayer.colour().equals(Blue)) {
				System.out.println(this.currentPlayer.location());
				System.out.println(this.mrX.location());
			}
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
