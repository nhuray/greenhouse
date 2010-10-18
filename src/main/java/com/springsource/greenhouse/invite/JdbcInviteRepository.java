package com.springsource.greenhouse.invite;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.springsource.greenhouse.account.Account;
import com.springsource.greenhouse.action.ActionRepository;
import com.springsource.greenhouse.action.ActionRepository.ActionFactory;
import com.springsource.greenhouse.connect.AccountReference;
import com.springsource.greenhouse.utils.Location;

@Repository
public class JdbcInviteRepository implements InviteRepository {

	private final JdbcTemplate jdbcTemplate;

	private final ActionRepository actionRepository;
	
	@Inject
	public JdbcInviteRepository(JdbcTemplate jdbcTemplate, ActionRepository actionRepository) {
		this.jdbcTemplate = jdbcTemplate;
		this.actionRepository = actionRepository;
	}
	
	public void saveInvite(String token, Invitee invitee, String text, Long sentBy) {
		jdbcTemplate.update(INSERT_INVITE, token, invitee.getEmail(), invitee.getFirstName(), invitee.getLastName(), text, sentBy);
	}

	public void markInviteAccepted(final String token, Account signedUp) {
		actionRepository.createAction(InviteAcceptAction.class, signedUp, new ActionFactory<InviteAcceptAction>() {
			public InviteAcceptAction createAction(Long id, DateTime performTime, Account account, Location location) {
				jdbcTemplate.update("insert into InviteAcceptAction (invite, memberAction) values (?, ?)", token, id);
				Map<String, Object> invite = jdbcTemplate.queryForMap("select sentBy, sentTime from Invite where token = ?", token);
				Long sentBy = (Long) invite.get("sentBy");
				DateTime sentTime = new DateTime(invite.get("sentTime"), DateTimeZone.UTC);
				return new InviteAcceptAction(id, performTime, account, location, sentBy, sentTime);
			}
		});
	}
	
	public Invite findInvite(String token) throws NoSuchInviteException, InviteAlreadyAcceptedException {
		Invite invite = queryForInvite(token);
		if (invite.isAccepted()) {
			throw new InviteAlreadyAcceptedException(token);
		}
		return invite;
	}
	
	private Invite queryForInvite(String token) throws NoSuchInviteException {
		try {
			return jdbcTemplate.queryForObject(SELECT_INVITE, new RowMapper<Invite>() {
				public Invite mapRow(ResultSet rs, int rowNum) throws SQLException {
					Invitee invitee = new Invitee(rs.getString("firstName"), rs.getString("lastName"), rs.getString("email"));
					AccountReference sentBy = AccountReference.textOnly(rs.getLong("sentById"), rs.getString("sentByUsername"), rs.getString("sentByFirstName"), rs.getString("sentByLastName"));
					return new Invite(invitee, sentBy, rs.getBoolean("accepted"));
				}
			}, token, token);
		} catch (EmptyResultDataAccessException e) {
			throw new NoSuchInviteException(token);
		}
	}

	private static final String INSERT_INVITE = "insert into Invite (token, email, firstName, lastName, text, sentBy) values (?, ?, ?, ?, ?, ?)";

	private static final String SELECT_INVITE = "select i.email, i.firstName, i.lastName, m.id as sentById, m.username as sentByUsername, m.firstName as sentByFirstName, m.lastName as sentByLastName, exists(select 1 from InviteAcceptAction where invite = ?) as accepted from Invite i inner join Member m on i.sentBy = m.id where i.token = ?";

}