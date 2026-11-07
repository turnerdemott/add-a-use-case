// UC-STU-deactivate-student (BR-student-lifecycle): a student deactivated off the team is listed in the peer
// evaluation form as an optional row for a week up to her deactivation. Logs in as the real John (Team1, id 1); the
// team list, rubric, existing evaluations, and submissions are stubbed, so these tests check what the form shows and
// sends. The backend's own tests cover who is listed and which evaluations it accepts.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const teamList = {
  flag: true,
  code: 200,
  message: 'Find students by team id successfully',
  data: [
    { id: 4, firstName: 'John', lastName: 'Smith', enabled: true, teamId: 1, teamName: 'Team1' },
    { id: 5, firstName: 'Eric', lastName: 'Hudson', enabled: true, teamId: 1, teamName: 'Team1' },
    { id: 6, firstName: 'Jerry', lastName: 'Moon', enabled: false } // Deactivated off Team1: on no team
  ]
}

const savedRow = (evaluationId, evaluateeId, evaluateeName) => ({
  evaluationId,
  week: '2023-W32',
  evaluatorId: 4,
  evaluateeId,
  evaluateeName,
  ratings: [{ criterionId: 1, criterion: 'Quality of work', actualScore: 9 }],
  publicComment: '',
  privateComment: ''
})

describe('Evaluate a deactivated former teammate', () => {
  beforeEach(() => {
    cy.login('j.smith@abc.edu', '123456')
    cy.intercept('GET', '**/students/teams/1*', { body: teamList }).as('teamList')
    cy.intercept('GET', '**/sections/2', {
      body: { flag: true, code: 200, message: '', data: { sectionId: 2, rubricId: 1 } }
    })
    cy.intercept('GET', '**/rubrics/1', {
      body: {
        flag: true,
        code: 200,
        message: '',
        data: {
          rubricId: 1,
          criteria: [
            { criterionId: 1, criterion: 'Quality of work', description: 'Quality', maxScore: 10 }
          ]
        }
      }
    })
    cy.intercept('POST', '**/evaluations', (req) => {
      req.reply({
        body: {
          flag: true,
          code: 200,
          message: 'Add evaluation successfully',
          data: { ...req.body, evaluationId: 200 + req.body.evaluateeId } // Saved, as the backend returns it
        }
      })
    }).as('submit')
    cy.intercept('PUT', '**/evaluations/*', (req) => {
      req.reply({
        body: { flag: true, code: 200, message: 'Update evaluation successfully', data: req.body }
      })
    }).as('update')
    cy.intercept('POST', '**/evaluations/weeks/*/receipt', {
      body: { flag: true, code: 200, message: '', data: null }
    })
  })

  // This week's evaluations John has already saved, as the page loads them
  function visitWithSavedEvaluations(saved) {
    cy.intercept('GET', '**/evaluations/evaluators/4/week/*', {
      body: { flag: true, code: 200, message: '', data: saved }
    })
    cy.visit('/evaluations/submit-evaluations')
  }

  it('asks for the team as of the week being evaluated and marks her row optional, unchecked', () => {
    visitWithSavedEvaluations([])

    cy.wait('@teamList')
      .its('request.query.week')
      .should('match', /^\d{4}-W\d{2}$/)

    cy.contains('tr', 'Jerry Moon')
      .find('[data-cy=optional-evaluatee]')
      .should('contain', 'deactivated, optional')
    cy.contains('tr', 'Jerry Moon')
      .find('[data-cy=evaluate-optional] input')
      .should('not.be.checked')
    cy.contains('tr', 'Eric Hudson').find('[data-cy=optional-evaluatee]').should('not.exist')
  })

  it('sends no evaluation of her when her row is left unchecked', () => {
    visitWithSavedEvaluations([])

    cy.contains('button', 'Submit').click()

    cy.contains('Peer evaluations submitted successfully')
    cy.get('@submit.all').should('have.length', 2)
    cy.get('@submit.all')
      .then((calls) => calls.map((call) => call.request.body.evaluateeId))
      .should('have.members', [4, 5])
  })

  it('sends one evaluation of her when her row is checked', () => {
    visitWithSavedEvaluations([])

    cy.contains('tr', 'Jerry Moon').find('[data-cy=evaluate-optional]').click()
    cy.contains('button', 'Submit').click()

    cy.contains('Peer evaluations submitted successfully')
    cy.get('@submit.all').should('have.length', 3)
    cy.get('@submit.all')
      .then((calls) => calls.map((call) => call.request.body.evaluateeId))
      .should('have.members', [4, 5, 6])
  })

  it('keeps her unchecked row after Submit, so she can still be evaluated with Update', () => {
    visitWithSavedEvaluations([])
    cy.contains('button', 'Submit').click()
    cy.contains('Peer evaluations submitted successfully')
    cy.contains('tr', 'Jerry Moon').find('[data-cy=evaluate-optional]').click()
    cy.contains('button', 'Update').click()

    cy.contains('Peer evaluations updated successfully')
    cy.get('@submit.all')
      .then((calls) => calls.map((call) => call.request.body.evaluateeId))
      .should('have.members', [4, 5, 6])
  })

  it('offers her row in update mode when the evaluator has not evaluated her yet', () => {
    visitWithSavedEvaluations([savedRow(101, 4, 'John Smith'), savedRow(102, 5, 'Eric Hudson')])
    cy.contains('tr', 'Jerry Moon').find('[data-cy=evaluate-optional]').click()
    cy.contains('button', 'Update').click()

    cy.contains('Peer evaluations updated successfully')
    cy.get('@submit.all')
      .then((calls) => calls.map((call) => call.request.body.evaluateeId))
      .should('have.members', [6])
    cy.get('@update.all')
      .then((calls) => calls.map((call) => call.request.body.evaluationId))
      .should('have.members', [101, 102])
  })

  it('shows no skip checkbox on a saved evaluation of her', () => {
    visitWithSavedEvaluations([
      savedRow(101, 4, 'John Smith'),
      savedRow(102, 5, 'Eric Hudson'),
      savedRow(103, 6, 'Jerry Moon')
    ])

    cy.contains('tr', 'Jerry Moon')
      .find('[data-cy=optional-evaluatee]')
      .should('contain', 'deactivated, optional')
    cy.contains('tr', 'Jerry Moon').find('[data-cy=evaluate-optional]').should('not.exist')
  })
})
