# Lexer (Tokenizer)

Converts a raw SQL string into a stream of tokens — the smallest meaningful units. The first stage in every SQL engine.

## What a Lexer Does

```
Input:  "SELECT name FROM users WHERE age > 25"

Output: [SELECT] [IDENTIFIER:name] [FROM] [IDENTIFIER:users]
        [WHERE] [IDENTIFIER:age] [GT] [INT_LITERAL:25]
```

The lexer doesn't understand SQL grammar — it just breaks text into pieces. Keywords vs identifiers, numbers vs strings, operators vs punctuation. The parser does the structural work.

## Why Lexer First (Not Combined)

Splitting lexing and parsing is an engineering choice:
- **Lexer** handles character-level details: whitespace, case folding, string escaping, comments
- **Parser** handles structure: grammar rules, precedence, AST construction

If you combined them, the parser would be cluttered with character-level noise. Separating them makes both simpler.

## Token Types

Every piece of input becomes a token with a type and an optional value:

```java
record Token(TokenType type, String value, int line, int column) {}
```

| Category | TokenType | Example | Has value? |
|----------|-----------|---------|------------|
| Keywords | SELECT, FROM, WHERE, INSERT, INTO, VALUES, CREATE, TABLE, DROP, UPDATE, SET, DELETE, ORDER, BY, ASC, DESC, LIMIT, AND, OR, NOT, IF, EXISTS, SHOW, DESCRIBE, DESC_TABLE, PRIMARY, KEY, INDEX, UNIQUE, NULL_KW, TRUE_KW, FALSE_KW | `SELECT`, `WHERE` | No |
| Identifiers | IDENTIFIER | `name`, `users`, `age` | Yes (the name) |
| Literals | INT_LITERAL, FLOAT_LITERAL, STRING_LITERAL | `25`, `3.14`, `'Alice'` | Yes (parsed value) |
| Operators | EQ, NEQ, LT, GT, LTE, GTE | `=`, `!=`, `<`, `>`, `<=`, `>=` | No |
| Math | PLUS, MINUS, STAR, SLASH | `+`, `-`, `*`, `/` | No |
| Punctuation | LPAREN, RPAREN, COMMA, SEMICOLON, DOT | `(`, `)`, `,`, `;`, `.` | No |

## How the Lexer Works: Character by Character

The lexer walks the input string one character at a time:

```
Input: "SELECT name FROM users WHERE age > 25"
        ↑
        cursor = 0, current char = 'S'
```

### 1. Skip whitespace
```
"  SELECT"  → skip spaces, start at 'S'
```

### 2. Read a word: is it a keyword or identifier?
```
"S" → "E" → "L" → "E" → "C" → "T" → " "
→ word = "SELECT"
→ check keyword table: "SELECT" is a keyword → token(SELECT)
```

```
"n" → "a" → "m" → "e" → " "
→ word = "name"
→ check keyword table: not found → token(IDENTIFIER, "name")
```

The keyword check is just a set lookup: `keywords.contains(word.toUpperCase())`.

### 3. Read a number
```
"2" → "5" → " "
→ digits only → token(INT_LITERAL, "25")
```

```
"3" → "." → "1" → "4" → " "
→ has a dot → token(FLOAT_LITERAL, "3.14")
```

### 4. Read a string
```
'\'' → 'A' → 'l' → 'i' → 'c' → 'e' → '\''
→ token(STRING_LITERAL, "'Alice'")
```

Handle escaped quotes: `'it''s'` → the string is `it's` (two single quotes inside = one escaped quote).

### 5. Read an operator
```
"!" → "=" → token(NEQ)
"=" → token(EQ)
"<" → "=" → token(LTE)
"<" → token(LT)
">" → "=" → token(GTE)
">" → token(GT)
```

Two-character operators (`<=`, `>=`, `!=`) are read greedily — peek ahead one character.

### 6. Single-character tokens
```
"(" → token(LPAREN)
")" → token(RPAREN)
"," → token(COMMA)
";" → token(SEMICOLON)
"*" → token(STAR)
```

## Comments

```sql
SELECT * FROM users -- this is a comment
WHERE age > 25
```

When the lexer hits `--`, it skips everything until the end of the line:

```java
if (current == '-' && peek() == '-') {
    while (current != '\n' && current != '\0') {
        advance();
    }
    continue; // skip this token
}
```

## Case Insensitivity

`select`, `SELECT`, `Select`, `SeLeCt` are all the same keyword. The lexer uppercases before keyword lookup:

```java
String upper = word.toUpperCase();
if (keywords.contains(upper)) {
    return new Token(TokenType.valueOf(upper), null, line, col);
}
return new Token(TokenType.IDENTIFIER, word, line, col); // preserve original case
```

Keywords are stored as uppercase (`SELECT`, `FROM`). Identifiers preserve their original case (`name`, `Name`, `NAME` are all the identifier text as-is).

## The Complete Lexer Interface

```java
class Lexer {
    private final String sql;
    private int pos;        // current position
    private int line;       // current line (for error messages)
    private int col;        // current column

    Lexer(String sql);

    Token nextToken();      // read and return next token
    Token peekToken();      // look ahead without consuming
    List<Token> tokenize(); // tokenize entire input (convenience)
}
```

## Example: Full Tokenization

```
SQL:     "SELECT * FROM users WHERE age > 25;"
Tokens:  SELECT STAR FROM IDENT(users) WHERE IDENT(age) GT INT(25) SEMICOLON EOF
```

Note: `*` is `STAR`, not `IDENTIFIER` or keyword. The lexer treats it as a special token because `*` is not a valid identifier character. In SQL, `SELECT *` means "all columns" — the parser (not the lexer) handles that meaning.

## What the Lexer Does NOT Do

- Does not check if `FROM` follows `SELECT`
- Does not check if `users` is a real table
- Does not check if `age` is a real column
- Does not validate anything

It just produces tokens. The parser validates structure. The binder validates meaning.

---

→ Next concept: [Parser](parser.md)
→ Related: [SQL Query Lifecycle](query-lifecycle.md)
