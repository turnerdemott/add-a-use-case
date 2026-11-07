// UC-STU-deactivate-student: the Deactivate action on the Students page. The student search and the deactivation are
// stubbed, so these tests check what the page offers, what the warning says, and what it sends; the backend's own
// tests cover who may deactivate and what deactivation records.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const john = {
  id: 4,
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  enabled: true,
  teamId: 1,
  teamName: 'Team1'
}
const tracy = {
  id: 15,
  firstName: 'Tracy',
  lastName: 'Nicholson',
  email: 't.nicholson@abc.edu',
  enabled: true
}
const jana = {
  id: 16,
  firstName: 'Jana',
  lastName: 'Norton',
  email: 'j.norton@abc.edu',
  enabled: false
}

const searchResult = {
  flag: true,
  code: 200,
  message: 'Find students successfully',
  data: { content: [john, tracy, jana], number: 0, size: 50, totalElements: 3 }
}

describe('Deactivate a student', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('POST', '**/students/search*', { body: searchResult }).as('search')
    cy.intercept('PATCH', '**/students/*/deactivate', {
      body: {
        flag: true,
        code: 200,
        message: 'Deactivate student successfully',
        data: { ...john, enabled: false, teamId: null, teamName: null }
      }
    }).as('deactivate')
    cy.visit('/students')
    cy.wait('@search')
  })

  it('offers Deactivate only for an active account (3a)', () => {
    cy.contains('tr', 'John').find('[data-cy=deactivate-student]').should('exist')
    cy.contains('tr', 'Jana').find('[data-cy=deactivate-student]').should('not.exist')
    cy.contains('tr', 'Jana').should('contain', 'Disabled')
  })

  it('warns with her name, the team she leaves, and the effects (step 4)', () => {
    cy.contains('tr', 'John').find('[data-cy=deactivate-student]').click()

    cy.get('[data-cy=deactivate-warning]')
      .should('contain', 'Deactivate John Smith?')
      .and('contain', 'John Smith will be removed from Team1.')
      .and('contain', 'will no longer be able to log in')
      .and('contain', 'weekly activity reports and peer evaluations are kept')
  })

  it('says she is on no team when she is on none (6b)', () => {
    cy.contains('tr', 'Tracy').find('[data-cy=deactivate-student]').click()

    cy.get('[data-cy=deactivate-warning]').should('contain', 'Tracy Nicholson is not on a team.')
  })

  it('sends nothing when the instructor cancels (5a)', () => {
    cy.contains('tr', 'John').find('[data-cy=deactivate-student]').click()
    cy.contains('.el-message-box button', 'Cancel').click()

    cy.get('.el-message-box').should('not.exist')
    cy.get('@deactivate.all').should('have.length', 0)
  })

  it('sends nothing when the instructor closes the warning (5a)', () => {
    cy.contains('tr', 'John').find('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box__headerbtn').click()

    cy.get('.el-message-box').should('not.exist')
    cy.get('@deactivate.all').should('have.length', 0)
  })

  it('deactivates her on Confirm and reloads the list (steps 5-6)', () => {
    cy.contains('tr', 'John').find('[data-cy=deactivate-student]').click()
    cy.contains('.el-message-box button', 'Deactivate').click()

    cy.wait('@deactivate')
      .its('request.url')
      .should('match', /\/students\/4\/deactivate$/)
    cy.contains('John Smith has been deactivated.')
    cy.wait('@search') // The reload after the deactivation
    cy.get('@deactivate.all').should('have.length', 1)
  })

  it('shows the refusal and reloads the list when she was already deactivated (6a)', () => {
    cy.intercept('PATCH', '**/students/*/deactivate', {
      statusCode: 409,
      body: { flag: false, code: 409, message: 'John Smith is already deactivated.', data: null }
    }).as('refused')

    cy.contains('tr', 'John').find('[data-cy=deactivate-student]').click()
    cy.contains('.el-message-box button', 'Deactivate').click()

    cy.wait('@refused')
    cy.contains('John Smith is already deactivated.')
    cy.wait('@search') // The reload shows her current status
  })
})
