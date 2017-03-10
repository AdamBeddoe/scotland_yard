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
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;

import uk.ac.bris.cs.gamekit.graph.Graph;

// TODO implement all methods and pass all tests
public class ScotlandYardModel implements ScotlandYardGame {
    private List<Boolean> rounds;
    private Graph<Integer, Transport> graph;
    private PlayerConfiguration mrX;
    private PlayerConfiguration firstDetective;
    private List<PlayerConfiguration> detectives = new ArrayList<PlayerConfiguration>();

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {
                this.rounds = requireNonNull(rounds);
                this.graph = requireNonNull(graph);
                this.mrX = requireNonNull(mrX);
                this.detectives.add(mrX);
                this.firstDetective = requireNonNull(firstDetective);
                this.detectives.add(firstDetective);

                for(PlayerConfiguration detective : restOfTheDetectives) {
                	this.detectives.add(requireNonNull(detective));
				}

				if (rounds.isEmpty()) throw new IllegalArgumentException("Empty rounds");
	 			if (graph.isEmpty()) throw new IllegalArgumentException("Empty map (graph)");
	 			if (mrX.colour != Black) throw new IllegalArgumentException("MrX should be Black");

	 			checkDuplicateLocations(detectives);
	 			checkDuplicateColours(detectives);
	 			checkDetectiveTickets(detectives);
	}

	// Checks all detectives for duplicate locations.
	private void checkDuplicateLocations(List<PlayerConfiguration> detectives) {
		Set<Integer> locations = new HashSet<>();
		for(PlayerConfiguration detective : detectives) {
			if (locations.contains(detective.location)) throw new IllegalArgumentException("Duplicate location");
			else locations.add(detective.location);
		}
	}

	// Checks all detectives for duplicate colours.
    private void checkDuplicateColours(List<PlayerConfiguration> detectives) {
        Set<Colour> colours = new HashSet<>();
        for(PlayerConfiguration detective : detectives) {
            if (colours.contains(detective.colour)) throw new IllegalArgumentException("Duplicate colour");
            else colours.add(detective.colour);
        }
    }

	// Validates the tickets of all detectives.
    private void checkDetectiveTickets(List<PlayerConfiguration> detectives) {
		for (PlayerConfiguration detective : detectives) {
			validateTicketTypes(detective);
			if (detective.equals(this.mrX)) validateMrXTickets(detective);
			else validateOtherDetectiveTickets(detective);
		}
    }

	// Checks if Mr X has the required number of each ticket.
	private void validateMrXTickets(PlayerConfiguration detective) {
		if (detective.tickets.get(Secret) != 5) throw new IllegalArgumentException("Mr X does not have secret ticket");
		if (detective.tickets.get(Double) != 2) throw new IllegalArgumentException("Mr X does not have double ticket");
		if (detective.tickets.get(Taxi) != 4) throw new IllegalArgumentException("Mr X does not have 4 Taxi tickets");
		if (detective.tickets.get(Bus) != 3) throw new IllegalArgumentException("Mr X does not have 3 Bus tickets");
		if (detective.tickets.get(Underground) != 3) throw new IllegalArgumentException("Mr X does not have 3 Underground tickets");

	}

	// Checks if a standard detective has the required number of each ticket.
	private void validateOtherDetectiveTickets(PlayerConfiguration detective) {
		if (detective.tickets.get(Secret) != 0) throw new IllegalArgumentException("Detective has a Secret ticket");
		if (detective.tickets.get(Double) != 0) throw new IllegalArgumentException("Detective has a Double ticket");
		if (detective.tickets.get(Taxi) != 11) throw new IllegalArgumentException("Detective does not have 10 Taxi tickets");
		if (detective.tickets.get(Bus) != 8) throw new IllegalArgumentException("Detective does not have 8 Bus tickets");
		if (detective.tickets.get(Underground) != 4) throw new IllegalArgumentException("Detective does not have 4 Underground tickets");
	}

	// Checks if the ticket map of a detective contains all the required ticket types.
	// Query whether checking length would be enough or whether this is better because of error information.
    private void validateTicketTypes(PlayerConfiguration detective) {
		if (!detective.tickets.containsKey(Secret)) throw new IllegalArgumentException("No ticket field for Secret");
		if (!detective.tickets.containsKey(Double)) throw new IllegalArgumentException("No ticket field for Double");
		if (!detective.tickets.containsKey(Taxi)) throw new IllegalArgumentException("No ticket field for Taxi");
		if (!detective.tickets.containsKey(Bus)) throw new IllegalArgumentException("No ticket field for Bus");
		if (!detective.tickets.containsKey(Underground)) throw new IllegalArgumentException("No ticket field for Underground");
	}

	@Override
	public void registerSpectator(Spectator spectator) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public void startRotate() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Collection<Spectator> getSpectators() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public List<Colour> getPlayers() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public int getPlayerLocation(Colour colour) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public int getPlayerTickets(Colour colour, Ticket ticket) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public boolean isGameOver() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Colour getCurrentPlayer() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public int getCurrentRound() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public boolean isRevealRound() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public List<Boolean> getRounds() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		// TODO
		throw new RuntimeException("Implement me");
	}

}
