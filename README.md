# model-generator-from-postgres
A project that can be useful in the very special case when you have a PostgreSQL DB, a JPA backend with DTOs as well, and if you use MapStruct for mapping between the latter two, and use TypeScript for frontend.
You only have to create a SQL file that creates the tables, then add its file path as an input.
From the table definitions the program will generate JPA Entity classes, Java DTO classes, MapStruct mapper interfaces and TypeScript DTO interfaces.
