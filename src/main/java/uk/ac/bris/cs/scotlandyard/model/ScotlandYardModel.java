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
public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move> {
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

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {
                this.rounds = requireNonNull(rounds);
                this.graph = requireNonNull(graph);
                requireNonNull(mrX);
                this.startPlayers.add(mrX);
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

		for (ScotlandYardPlayer p : playerList) {
			this.currentPlayer = p;
			Player player = p.player();
			if (p == mrX) {
                this.availableMoves = validMovesMrX();
				Set<Move> playerMoves = unmodifiableSet(this.availableMoves);
			    player.makeMove(this, p.location(), playerMoves, this);
				this.roundNum++;
				notifyLoop((Spectator s) -> s.onRoundStarted(this, roundNum));
			}
            else {
                this.availableMoves = validMoves();
				if (availableMoves.isEmpty()) availableMoves.add(new PassMove(currentPlayer.colour()));
				Set<Move> playerMoves = unmodifiableSet(this.availableMoves);
			    player.makeMove(this, p.location(), playerMoves, this);
            }
		}
		this.roundNum++;
		notifyLoop((Spectator s) -> s.onRotationComplete(this));
	}

	private Set<Move> validMoves() {
        Set<Move> valid = new HashSet<>();
        int loc = this.currentPlayer.location();
        Node node = this.graph.getNode(loc);
        Collection<Edge> edges = this.graph.getEdgesFrom(node);
        boolean occupied;

        for (Edge edge : edges) {
            occupied = false;
            Transport t = (Transport) edge.data();
            Ticket ticket = Ticket.fromTransport(t);
            if (currentPlayer.hasTickets(ticket)) {
            	for (ScotlandYardPlayer player : playerList) {
            		if (player.location() == ((Integer) edge.destination().value()) && !player.equals(this.mrX)) occupied = true;
				}

				if (!occupied) {
					Move move = new TicketMove(currentPlayer.colour(), ticket, (Integer) edge.destination().value());
					valid.add(move);
				}
            }

        }
        return valid;
	}

    private Set<Move> validMovesMrX() {
		Set<Move> valid = validMoves();
        Set<Move> doubleValid = new HashSet<>();
		int loc = this.currentPlayer.location();
		Node node = this.graph.getNode(loc);
		Collection<Edge> firstEdges = this.graph.getEdgesFrom(node);
		boolean occupied;

		for (Edge firstEdge : firstEdges) {
		    occupied = false;
			if (currentPlayer.hasTickets(Secret)) {
				for (ScotlandYardPlayer player : playerList) {
					if (player.location() == ((Integer) firstEdge.destination().value()) && !player.equals(this.mrX)) occupied = true;
				}

				if (!occupied) {
					Move move = new TicketMove(Black, Secret, (Integer) firstEdge.destination().value());
					valid.add(move);
				}
			}

            if (currentPlayer.hasTickets(Double)) {
			    for (Move move : valid) {
			        for (Move secondMove : validMovesFrom(move)) {
			            Move move3 = new DoubleMove(Black,(TicketMove) move,(TicketMove) secondMove);
                        doubleValid.add(move3);
                    }
                }
            }

		}
        doubleValid.addAll(valid);
		return doubleValid;
    }

    private Set<Move> validMovesFrom(Move move) {
        Set<Move> validMoves = new HashSet<>();
        boolean occupied;
        if (move instanceof TicketMove) {
            Node node = this.graph.getNode(((TicketMove) move).destination());
            Collection<Edge> edges = this.graph.getEdgesFrom(node);
            for (Edge edge : edges) {
                occupied = false;
                Transport t = (Transport) edge.data();
                Ticket ticket = Ticket.fromTransport(t);
                if (currentPlayer.hasTickets(ticket)) {
                    for (ScotlandYardPlayer player : playerList) {
                        if (player.location() == ((Integer) edge.destination().value()) && !player.equals(this.mrX)) occupied = true;
                    }

                    if (!occupied) {
                        Move newMove = new TicketMove(currentPlayer.colour(), ticket, (Integer) edge.destination().value());
                        validMoves.add(newMove);
                        if (currentPlayer.hasTickets(Secret)) {
                            Move secretMove = new TicketMove(currentPlayer.colour(), Secret, (Integer) edge.destination().value());
                            validMoves.add(secretMove);
                        }
                    }
                }

            }
        }
        return validMoves;
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

		for(PlayerConfiguration player : startPlayers) {
			if(player.colour.equals(Black)){
				if(!getRounds().get(getRounds().size()-1)) location = 0;
				else return player.location;
			}
			else if(player.colour.equals(colour)) {
				location = player.location;
			}
		}

		return location;
	}

	@Override
	public int getPlayerTickets(Colour colour, Ticket ticket) {
		for(PlayerConfiguration player : startPlayers) {
			if(player.colour.equals(colour)) {
				return player.tickets.get(ticket);
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
	    if (move == null) throw new NullPointerException();
	    if (!this.availableMoves.contains(move) && this.currentPlayer == this.mrX) throw new IllegalArgumentException("Mr X move not in valid moves");
        else if (!this.availableMoves.contains(move)) throw new IllegalArgumentException("Detective move not in valid moves");

    }

	private void notifyLoop(NotifyFunction function) {
		for (Spectator spectator : spectators){
			function.notifyFunc(spectator);
		}
	}

}

interface NotifyFunction {
	public void notifyFunc(Spectator spectator);
}