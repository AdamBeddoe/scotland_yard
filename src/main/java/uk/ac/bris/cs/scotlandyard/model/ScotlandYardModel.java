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
		notifyLoop(spectator -> spectator.onRoundStarted(this, roundNum));
        this.availableMoves = validMovesMrX();
		Set<Move> playerMoves = unmodifiableSet(this.availableMoves);
		Player player = this.currentPlayer.player();
		player.makeMove(this, this.currentPlayer.location(), playerMoves, this);
		notifyLoop(spectator -> spectator.onRoundStarted(this, getCurrentRound()));
			/*
            else {
                this.availableMoves = validMoves();
				if (availableMoves.isEmpty()) availableMoves.add(new PassMove(currentPlayer.colour()));
				Set<Move> playerMoves = unmodifiableSet(this.availableMoves);
			    player.makeMove(this, p.location(), playerMoves, this);
            }
            */


		//notifyLoop(spectator -> spectator.onRotationComplete(this));
	}

	// Creates a set of valid moves for a detective.
	private Set<Move> validMoves() {
        Set<Move> validMoves = new HashSet<>();
        int loc = this.currentPlayer.location();
        Node node = this.graph.getNode(loc);
        Collection<Edge> edges = this.graph.getEdgesFrom(node);

        for (Edge edge : edges) {
            Transport t = (Transport) edge.data();
            Ticket ticket = Ticket.fromTransport(t);
            if (currentPlayer.hasTickets(ticket) && !nodeOccupied(edge)) {
                Move move = new TicketMove(currentPlayer.colour(), ticket, (Integer) edge.destination().value());
				validMoves.add(move);
            }

        }
        return validMoves;
	}

	// Creates a set of valid moves for MrX.
    private Set<Move> validMovesMrX() {
		//Can mrX move to his own spot?
		Set<Move> firstMoves = validMoves();
        Set<Move> validMoves = new HashSet<>();
		int loc = this.currentPlayer.location();
		Node node = this.graph.getNode(loc);

		Collection<Edge> firstEdges = this.graph.getEdgesFrom(node);
		for (Edge firstEdge : firstEdges) {
			if (currentPlayer.hasTickets(Secret) && !nodeOccupied(firstEdge)) {
				Move regularMove = new TicketMove(Black, Secret, (Integer) firstEdge.destination().value());
				firstMoves.add(regularMove);
			}

            if (currentPlayer.hasTickets(Double) && this.roundNum <= (rounds.size()-2)) {
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
			if (player.location() == ((Integer) edge.destination().value()) && !player.equals(this.mrX)) return true;
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
		Set<Colour> winners = new HashSet<>();

	    return Collections.unmodifiableSet(winners);
	}

	@Override
	public int getPlayerLocation(Colour colour) {
		int location = 0;

		for(ScotlandYardPlayer player : playerList) {
			if(player.colour().equals(Black)){
				if(!isRevealRound()) location = 0; //Logic should be sound
				else return player.location();
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
		return false;
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
		//notifyLoop(spectator -> spectator.onRoundStarted(this, getCurrentRound()));

	    if (move == null) throw new NullPointerException();
	    if (!this.availableMoves.contains(move) && this.currentPlayer == this.mrX) throw new IllegalArgumentException("Mr X move not in valid moves");
        else if (!this.availableMoves.contains(move)) throw new IllegalArgumentException("Detective move not in valid moves");
        System.out.println(currentPlayer.colour() + " " + currentPlayer.tickets());
        move.visit(this);
        System.out.println(currentPlayer.colour() + " " + currentPlayer.tickets());
		notifyLoop(spectator -> spectator.onMoveMade(this, move));

        this.playerNum++;
        if (playerNum < this.playerList.size()) {
            System.out.println("Player: " + this.currentPlayer.colour());
            System.out.println("out of " + this.playerList);
            this.currentPlayer = this.playerList.get(playerNum);
            Player player = this.currentPlayer.player();
            this.availableMoves = validMoves();
            if (availableMoves.isEmpty()) availableMoves.add(new PassMove(currentPlayer.colour()));
            Set<Move> playerMoves = unmodifiableSet(this.availableMoves);
            player.makeMove(this, this.currentPlayer.location(), playerMoves, this);


		}
        else {
			this.roundNum++;
            this.playerNum = 0;
			notifyLoop(spectator -> spectator.onRotationComplete(this));
		}


    }

	private void notifyLoop(NotifyFunction function) {
		for (Spectator spectator : spectators){
			function.notifyFunc(spectator);
		}
	}

	public void visit(PassMove move) {
	}

	public void visit(TicketMove move) {
		this.currentPlayer.removeTicket(move.ticket());
		this.currentPlayer.location(move.destination());
	}
    /*
	public void visit(DoubleMove move) {
		this.currentPlayer.removeTicket(Double);
		this.currentPlayer.location(move.finalDestination());
	}
	*/
}

interface NotifyFunction {
	void notifyFunc(Spectator spectator);
}