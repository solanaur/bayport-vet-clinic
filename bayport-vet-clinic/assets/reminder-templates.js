window.REMINDER_TEMPLATES = [
  {
    id: "vaccine",
    name: "Vaccine Booster Reminder",
    subject: "Vaccine Booster Reminder for {{petName}}",
    template: 
`Hello {{ownerName}},

This is a friendly reminder from Bayport Veterinary Clinic.

Your pet {{petName}} is due for their vaccine booster on {{date}}.
Please schedule an appointment at your convenience.

Thank you,
Bayport Veterinary Clinic`
  },

  {
    id: "followup",
    name: "Follow-Up Check-up Reminder",
    subject: "Follow-Up Appointment for {{petName}}",
    template:
`Hello {{ownerName}},

We hope {{petName}} is doing well!

This is a reminder for their follow-up appointment on {{date}}.
Contact us if you need to adjust the schedule.

Thank you,
Bayport Veterinary Clinic`
  },

  {
    id: "general",
    name: "General Reminder",
    subject: "Reminder from Bayport Veterinary Clinic",
    template:
`Hello {{ownerName}},

{{message}}

Best regards,
Bayport Veterinary Clinic`
  }
];
